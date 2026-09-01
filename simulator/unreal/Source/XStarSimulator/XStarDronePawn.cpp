#include "XStarDronePawn.h"

#include "Camera/CameraComponent.h"
#include "Components/SceneComponent.h"
#include "Components/StaticMeshComponent.h"
#include "GameFramework/SpringArmComponent.h"
#include "UObject/ConstructorHelpers.h"

AXStarDronePawn::AXStarDronePawn()
{
    PrimaryActorTick.bCanEverTick = true;
    DroneRoot = CreateDefaultSubobject<USceneComponent>(TEXT("DroneRoot"));
    SetRootComponent(DroneRoot);

    Body = CreateDefaultSubobject<UStaticMeshComponent>(TEXT("Body"));
    Body->SetupAttachment(DroneRoot);
    static ConstructorHelpers::FObjectFinder<UStaticMesh> Cube(TEXT("/Engine/BasicShapes/Cube.Cube"));
    if (Cube.Succeeded())
    {
        Body->SetStaticMesh(Cube.Object);
        Body->SetRelativeScale3D(FVector(0.8f, 0.55f, 0.18f));
    }

    CameraGimbal = CreateDefaultSubobject<USceneComponent>(TEXT("CameraGimbal"));
    CameraGimbal->SetupAttachment(DroneRoot);
    CameraGimbal->SetRelativeLocation(FVector(35.0f, 0.0f, -25.0f));

    SpringArm = CreateDefaultSubobject<USpringArmComponent>(TEXT("SpringArm"));
    SpringArm->SetupAttachment(DroneRoot);
    SpringArm->TargetArmLength = 500.0f;
    SpringArm->SetRelativeRotation(FRotator(-18.0f, 0.0f, 0.0f));
    SpringArm->bEnableCameraLag = true;
    SpringArm->CameraLagSpeed = 5.0f;

    ChaseCamera = CreateDefaultSubobject<UCameraComponent>(TEXT("ChaseCamera"));
    ChaseCamera->SetupAttachment(SpringArm, USpringArmComponent::SocketName);

    TelemetryReceiver = CreateDefaultSubobject<UXStarTelemetryReceiverComponent>(TEXT("TelemetryReceiver"));
    AutoPossessPlayer = EAutoReceiveInput::Player0;
}

void AXStarDronePawn::Tick(float DeltaSeconds)
{
    Super::Tick(DeltaSeconds);
    if (!TelemetryReceiver || !TelemetryReceiver->bReceiving)
    {
        return;
    }
    const FXStarSimulatorTelemetry& Telemetry = TelemetryReceiver->LatestTelemetry;
    if (!bHasTelemetryOrigin)
    {
        OriginLatitudeDeg = Telemetry.HomeLatitudeDeg != 0.0 ? Telemetry.HomeLatitudeDeg : Telemetry.LatitudeDeg;
        OriginLongitudeDeg = Telemetry.HomeLongitudeDeg != 0.0 ? Telemetry.HomeLongitudeDeg : Telemetry.LongitudeDeg;
        bHasTelemetryOrigin = true;
    }
    const double NorthM = (Telemetry.LatitudeDeg - OriginLatitudeDeg) * 111111.0;
    const double EastM = (Telemetry.LongitudeDeg - OriginLongitudeDeg) * 83000.0;
    const FVector TargetLocation(NorthM * 100.0, EastM * 100.0, Telemetry.AltitudeM * 100.0 + 100.0);
    const FRotator TargetRotation(Telemetry.PitchDeg, Telemetry.YawDeg, Telemetry.RollDeg);
    SetActorLocationAndRotation(
        FMath::VInterpTo(GetActorLocation(), TargetLocation, DeltaSeconds, 10.0f),
        FMath::RInterpTo(GetActorRotation(), TargetRotation, DeltaSeconds, 10.0f)
    );
    CameraGimbal->SetRelativeRotation(FRotator(Telemetry.GimbalPitchDeg, 0.0f, 0.0f));
}
