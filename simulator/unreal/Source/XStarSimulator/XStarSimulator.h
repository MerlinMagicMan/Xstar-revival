#pragma once

#include "CoreMinimal.h"
#include "Modules/ModuleManager.h"

class IPixelStreaming2Module;

namespace UE::PixelStreaming2Servers
{
class IServer;
}

class FXStarSimulatorModule final : public FDefaultGameModuleImpl
{
public:
    virtual void StartupModule() override;
    virtual void ShutdownModule() override;

private:
    void StartVideoOnlyViewer();
    void ConfigureVideoOnlyStreamer(IPixelStreaming2Module& PixelStreamingModule);

    FDelegateHandle PixelStreamingReadyHandle;
    TSharedPtr<UE::PixelStreaming2Servers::IServer> SignallingServer;
};
