#pragma once

#include "CoreMinimal.h"
#include "Common/UdpSocketReceiver.h"
#include "Components/ActorComponent.h"
#include "XStarTelemetryReceiverComponent.generated.h"

class FSocket;
class FArrayReader;
struct FIPv4Endpoint;

USTRUCT(BlueprintType)
struct FXStarSimulatorTelemetry
{
    GENERATED_BODY()

    UPROPERTY(BlueprintReadOnly) int64 Sequence = 0;
    UPROPERTY(BlueprintReadOnly) FString Phase = TEXT("DISCONNECTED");
    UPROPERTY(BlueprintReadOnly) bool bArmed = false;
    UPROPERTY(BlueprintReadOnly) double LatitudeDeg = 0.0;
    UPROPERTY(BlueprintReadOnly) double LongitudeDeg = 0.0;
    UPROPERTY(BlueprintReadOnly) double HomeLatitudeDeg = 0.0;
    UPROPERTY(BlueprintReadOnly) double HomeLongitudeDeg = 0.0;
    UPROPERTY(BlueprintReadOnly) double AltitudeM = 0.0;
    UPROPERTY(BlueprintReadOnly) double GroundSpeedMps = 0.0;
    UPROPERTY(BlueprintReadOnly) double VerticalSpeedMps = 0.0;
    UPROPERTY(BlueprintReadOnly) double RollDeg = 0.0;
    UPROPERTY(BlueprintReadOnly) double PitchDeg = 0.0;
    UPROPERTY(BlueprintReadOnly) double YawDeg = 0.0;
    UPROPERTY(BlueprintReadOnly) double Throttle = 0.0;
    UPROPERTY(BlueprintReadOnly) double YawInput = 0.0;
    UPROPERTY(BlueprintReadOnly) double PitchInput = 0.0;
    UPROPERTY(BlueprintReadOnly) double RollInput = 0.0;
    UPROPERTY(BlueprintReadOnly) double GimbalInput = 0.0;
    UPROPERTY(BlueprintReadOnly) double GimbalPitchDeg = 0.0;
    UPROPERTY(BlueprintReadOnly) FString ViewMode = TEXT("FPV");
    UPROPERTY(BlueprintReadOnly) double BatteryPercent = 0.0;
    UPROPERTY(BlueprintReadOnly) bool bRecording = false;
};

DECLARE_DYNAMIC_MULTICAST_DELEGATE_OneParam(FXStarTelemetryReceived, const FXStarSimulatorTelemetry&, Telemetry);

/** Receive-only endpoint for protocol v1 simulator telemetry on UDP port 46000. */
UCLASS(ClassGroup=(XStar), meta=(BlueprintSpawnableComponent))
class XSTARSIMULATOR_API UXStarTelemetryReceiverComponent : public UActorComponent
{
    GENERATED_BODY()

public:
    UXStarTelemetryReceiverComponent();

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category="X-Star Simulator")
    int32 ListenPort = 46000;

    UPROPERTY(BlueprintAssignable, Category="X-Star Simulator")
    FXStarTelemetryReceived OnTelemetryReceived;

    UPROPERTY(BlueprintReadOnly, Category="X-Star Simulator")
    FXStarSimulatorTelemetry LatestTelemetry;

    UPROPERTY(BlueprintReadOnly, Category="X-Star Simulator")
    bool bReceiving = false;

protected:
    virtual void BeginPlay() override;
    virtual void EndPlay(const EEndPlayReason::Type EndPlayReason) override;

private:
    FSocket* Socket = nullptr;
    TUniquePtr<FUdpSocketReceiver> Receiver;
    bool bLoggedFirstTelemetry = false;

    void StartReceiver();
    void StopReceiver();
    void HandleDatagram(const TSharedPtr<FArrayReader, ESPMode::ThreadSafe>& Data, const FIPv4Endpoint& Endpoint);
    static bool ParseTelemetry(const FString& Json, FXStarSimulatorTelemetry& OutTelemetry);
};
