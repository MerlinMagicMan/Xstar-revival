#include "XStarTelemetryReceiverComponent.h"

#include "Async/Async.h"
#include "Common/UdpSocketBuilder.h"
#include "Common/UdpSocketReceiver.h"
#include "Dom/JsonObject.h"
#include "HAL/RunnableThread.h"
#include "Interfaces/IPv4/IPv4Endpoint.h"
#include "Serialization/ArrayReader.h"
#include "Serialization/JsonReader.h"
#include "Serialization/JsonSerializer.h"
#include "Sockets.h"
#include "SocketSubsystem.h"

namespace
{
constexpr int32 ProtocolVersion = 1;
const FString ProtocolName = TEXT("xstar-simulator");

double OptionalNumber(const TSharedPtr<FJsonObject>& Object, const TCHAR* Field)
{
    double Value = 0.0;
    Object->TryGetNumberField(Field, Value);
    return FMath::IsFinite(Value) ? Value : 0.0;
}
}

UXStarTelemetryReceiverComponent::UXStarTelemetryReceiverComponent()
{
    PrimaryComponentTick.bCanEverTick = false;
}

void UXStarTelemetryReceiverComponent::BeginPlay()
{
    Super::BeginPlay();
    StartReceiver();
}

void UXStarTelemetryReceiverComponent::EndPlay(const EEndPlayReason::Type EndPlayReason)
{
    StopReceiver();
    Super::EndPlay(EndPlayReason);
}

void UXStarTelemetryReceiverComponent::StartReceiver()
{
    StopReceiver();
    Socket = FUdpSocketBuilder(TEXT("XStarSimulatorTelemetry"))
        .AsNonBlocking()
        .AsReusable()
        .BoundToAddress(FIPv4Address::Any)
        .BoundToPort(ListenPort)
        .WithReceiveBufferSize(2 * 1024 * 1024);
    if (!Socket)
    {
        UE_LOG(LogTemp, Error, TEXT("X-Star simulator could not bind UDP port %d"), ListenPort);
        return;
    }
    Receiver = MakeUnique<FUdpSocketReceiver>(Socket, FTimespan::FromMilliseconds(10), TEXT("XStarSimulatorReceiver"));
    Receiver->OnDataReceived().BindUObject(this, &UXStarTelemetryReceiverComponent::HandleDatagram);
    Receiver->Start();
    UE_LOG(LogTemp, Display, TEXT("X-Star simulator listening on UDP %d"), ListenPort);
}

void UXStarTelemetryReceiverComponent::StopReceiver()
{
    if (Receiver)
    {
        Receiver->Stop();
        Receiver.Reset();
    }
    if (Socket)
    {
        ISocketSubsystem::Get(PLATFORM_SOCKETSUBSYSTEM)->DestroySocket(Socket);
        Socket = nullptr;
    }
    bReceiving = false;
}

void UXStarTelemetryReceiverComponent::HandleDatagram(
    const TSharedPtr<FArrayReader, ESPMode::ThreadSafe>& Data,
    const FIPv4Endpoint& Endpoint)
{
    if (!Data.IsValid() || Data->Num() == 0 || Data->Num() > 64 * 1024)
    {
        return;
    }
    FUTF8ToTCHAR Converted(reinterpret_cast<const ANSICHAR*>(Data->GetData()), Data->Num());
    const FString Json(Converted.Length(), Converted.Get());
    FXStarSimulatorTelemetry Parsed;
    if (!ParseTelemetry(Json, Parsed))
    {
        UE_LOG(LogTemp, Warning, TEXT("Ignored invalid simulator datagram from %s"), *Endpoint.ToString());
        return;
    }
    TWeakObjectPtr<UXStarTelemetryReceiverComponent> WeakThis(this);
    AsyncTask(ENamedThreads::GameThread, [WeakThis, Parsed]()
    {
        if (!WeakThis.IsValid())
        {
            return;
        }
        WeakThis->LatestTelemetry = Parsed;
        WeakThis->bReceiving = true;
        WeakThis->OnTelemetryReceived.Broadcast(Parsed);
    });
}

bool UXStarTelemetryReceiverComponent::ParseTelemetry(const FString& Json, FXStarSimulatorTelemetry& OutTelemetry)
{
    TSharedPtr<FJsonObject> Root;
    const TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(Json);
    if (!FJsonSerializer::Deserialize(Reader, Root) || !Root.IsValid())
    {
        return false;
    }
    FString Protocol;
    FString Type;
    double Version = 0.0;
    bool bSimulated = false;
    if (!Root->TryGetStringField(TEXT("protocol"), Protocol) || Protocol != ProtocolName ||
        !Root->TryGetNumberField(TEXT("version"), Version) ||
        !FMath::IsNearlyEqual(Version, static_cast<double>(ProtocolVersion)) ||
        !Root->TryGetStringField(TEXT("type"), Type) || Type != TEXT("telemetry") ||
        !Root->TryGetBoolField(TEXT("simulated"), bSimulated) || !bSimulated)
    {
        return false;
    }
    const TSharedPtr<FJsonObject>* Aircraft = nullptr;
    const TSharedPtr<FJsonObject>* Controller = nullptr;
    const TSharedPtr<FJsonObject>* Battery = nullptr;
    const TSharedPtr<FJsonObject>* Camera = nullptr;
    if (!Root->TryGetObjectField(TEXT("aircraft"), Aircraft) ||
        !Root->TryGetObjectField(TEXT("controller"), Controller) ||
        !Root->TryGetObjectField(TEXT("battery"), Battery) ||
        !Root->TryGetObjectField(TEXT("camera"), Camera))
    {
        return false;
    }
    double Sequence = 0.0;
    if (!Root->TryGetNumberField(TEXT("sequence"), Sequence) ||
        !FMath::IsFinite(Sequence) || Sequence < 0.0 || !FMath::IsNearlyEqual(Sequence, FMath::RoundToDouble(Sequence)))
    {
        return false;
    }
    OutTelemetry.Sequence = static_cast<int64>(Sequence);
    (*Aircraft)->TryGetStringField(TEXT("phase"), OutTelemetry.Phase);
    (*Aircraft)->TryGetBoolField(TEXT("armed"), OutTelemetry.bArmed);
    OutTelemetry.LatitudeDeg = OptionalNumber(*Aircraft, TEXT("latitudeDeg"));
    OutTelemetry.LongitudeDeg = OptionalNumber(*Aircraft, TEXT("longitudeDeg"));
    OutTelemetry.HomeLatitudeDeg = OptionalNumber(*Aircraft, TEXT("homeLatitudeDeg"));
    OutTelemetry.HomeLongitudeDeg = OptionalNumber(*Aircraft, TEXT("homeLongitudeDeg"));
    OutTelemetry.AltitudeM = OptionalNumber(*Aircraft, TEXT("altitudeM"));
    OutTelemetry.GroundSpeedMps = OptionalNumber(*Aircraft, TEXT("groundSpeedMps"));
    OutTelemetry.VerticalSpeedMps = OptionalNumber(*Aircraft, TEXT("verticalSpeedMps"));
    OutTelemetry.RollDeg = OptionalNumber(*Aircraft, TEXT("rollDeg"));
    OutTelemetry.PitchDeg = OptionalNumber(*Aircraft, TEXT("pitchDeg"));
    OutTelemetry.YawDeg = OptionalNumber(*Aircraft, TEXT("yawDeg"));
    OutTelemetry.Throttle = OptionalNumber(*Controller, TEXT("throttle"));
    OutTelemetry.YawInput = OptionalNumber(*Controller, TEXT("yaw"));
    OutTelemetry.PitchInput = OptionalNumber(*Controller, TEXT("pitch"));
    OutTelemetry.RollInput = OptionalNumber(*Controller, TEXT("roll"));
    OutTelemetry.GimbalInput = OptionalNumber(*Controller, TEXT("gimbal"));
    OutTelemetry.BatteryPercent = OptionalNumber(*Battery, TEXT("percent"));
    OutTelemetry.GimbalPitchDeg = OptionalNumber(*Camera, TEXT("gimbalPitchDeg"));
    (*Camera)->TryGetBoolField(TEXT("recording"), OutTelemetry.bRecording);
    return true;
}
