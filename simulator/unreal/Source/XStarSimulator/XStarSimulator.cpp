#include "XStarSimulator.h"
#include "Async/Async.h"
#include "HAL/PlatformMisc.h"
#include "IPixelStreaming2Module.h"
#include "IPixelStreaming2Streamer.h"
#include "Misc/CommandLine.h"
#include "Misc/Parse.h"
#include "Modules/ModuleManager.h"
#include "PixelStreaming2Servers.h"

IMPLEMENT_PRIMARY_GAME_MODULE(FXStarSimulatorModule, XStarSimulator, "XStarSimulator");

void FXStarSimulatorModule::StartupModule()
{
    FDefaultGameModuleImpl::StartupModule();
    if (!FParse::Param(FCommandLine::Get(), TEXT("XStarPixelStreaming")))
    {
        return;
    }

    StartVideoOnlyViewer();
    IPixelStreaming2Module& PixelStreamingModule = IPixelStreaming2Module::Get();
    if (PixelStreamingModule.IsReady())
    {
        ConfigureVideoOnlyStreamer(PixelStreamingModule);
    }
    else
    {
        PixelStreamingReadyHandle = PixelStreamingModule.OnReady().AddRaw(
            this,
            &FXStarSimulatorModule::ConfigureVideoOnlyStreamer
        );
    }
}

void FXStarSimulatorModule::ShutdownModule()
{
    if (IPixelStreaming2Module::IsAvailable() && PixelStreamingReadyHandle.IsValid())
    {
        IPixelStreaming2Module::Get().OnReady().Remove(PixelStreamingReadyHandle);
        PixelStreamingReadyHandle.Reset();
    }
    if (SignallingServer.IsValid())
    {
        SignallingServer->Stop();
        SignallingServer.Reset();
    }
    FDefaultGameModuleImpl::ShutdownModule();
}

void FXStarSimulatorModule::StartVideoOnlyViewer()
{
    SignallingServer = UE::PixelStreaming2Servers::MakeSignallingServer();
    UE::PixelStreaming2Servers::FLaunchArgs LaunchArgs;
    LaunchArgs.bPollUntilReady = false;
    LaunchArgs.ProcessArgs = TEXT(
        "--HttpPort=8080 --StreamerPort=8888 --ServeHttps=false --EnableIPv6=true "
        "--AllowedOrigins=http://127.0.0.1,http://localhost,http://*.local:8080,http://192.168.*.*:8080,http://10.*.*.*:8080,http://172.*.*.*:8080"
    );
    if (!SignallingServer->Launch(LaunchArgs))
    {
        UE_LOG(LogTemp, Error, TEXT("X-Star could not start the local Pixel Streaming viewer on port 8080"));
        SignallingServer.Reset();
        return;
    }
    UE_LOG(LogTemp, Display, TEXT("X-Star local video-only viewer started on http://0.0.0.0:8080"));
}

void FXStarSimulatorModule::ConfigureVideoOnlyStreamer(IPixelStreaming2Module& PixelStreamingModule)
{
    if (PixelStreamingReadyHandle.IsValid())
    {
        PixelStreamingModule.OnReady().Remove(PixelStreamingReadyHandle);
        PixelStreamingReadyHandle.Reset();
    }

    // Pixel Streaming broadcasts readiness immediately before it creates its default streamer.
    // Defer one game-thread task so the real RTC factory and streamer exist before hardening it.
    AsyncTask(ENamedThreads::GameThread, []()
    {
        if (!IPixelStreaming2Module::IsAvailable())
        {
            return;
        }
        IPixelStreaming2Module& Module = IPixelStreaming2Module::Get();
        const FString StreamerId = Module.GetDefaultStreamerID();
        TSharedPtr<IPixelStreaming2Streamer> Streamer = Module.FindStreamer(StreamerId);
        if (!Streamer.IsValid())
        {
            Streamer = Module.CreateStreamer(StreamerId);
        }
        if (Streamer->GetConnectionURL().IsEmpty())
        {
            Streamer->SetConnectionURL(TEXT("ws://127.0.0.1:8888"));
        }

        // The Pixel Streaming protocol keeps its data channel for session setup. The Android
        // viewer consumes touch, key, mouse, and gamepad events before they reach that channel;
        // flight commands continue exclusively through the isolated simulator controller path.
        if (!Streamer->IsStreaming())
        {
            Streamer->StartStreaming();
        }
        UE_LOG(LogTemp, Display, TEXT("X-Star Pixel Streaming viewer is ready; Android remote input is blocked client-side"));
    });
}
