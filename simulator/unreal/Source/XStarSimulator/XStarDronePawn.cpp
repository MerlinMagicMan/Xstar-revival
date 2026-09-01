#include "XStarDronePawn.h"

#include "Camera/CameraComponent.h"
#include "Components/SceneComponent.h"
#include "Components/StaticMeshComponent.h"
#include "Engine/StaticMesh.h"
#include "GameFramework/SpringArmComponent.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "UObject/ConstructorHelpers.h"

AXStarDronePawn::AXStarDronePawn()
{
    PrimaryActorTick.bCanEverTick = true;
    DroneRoot = CreateDefaultSubobject<USceneComponent>(TEXT("DroneRoot"));
    SetRootComponent(DroneRoot);

    static ConstructorHelpers::FObjectFinder<UStaticMesh> Cube(TEXT("/Engine/BasicShapes/Cube.Cube"));
    static ConstructorHelpers::FObjectFinder<UStaticMesh> Sphere(TEXT("/Engine/BasicShapes/Sphere.Sphere"));
    static ConstructorHelpers::FObjectFinder<UStaticMesh> Cylinder(TEXT("/Engine/BasicShapes/Cylinder.Cylinder"));
    auto AddPart = [this](
        const FName Name,
        UStaticMesh* Mesh,
        const FVector& Location,
        const FRotator& Rotation,
        const FVector& Scale)
    {
        UStaticMeshComponent* Part = CreateDefaultSubobject<UStaticMeshComponent>(Name);
        Part->SetupAttachment(DroneRoot);
        Part->SetStaticMesh(Mesh);
        Part->SetRelativeLocation(Location);
        Part->SetRelativeRotation(Rotation);
        Part->SetRelativeScale3D(Scale);
        Part->SetCollisionEnabled(ECollisionEnabled::NoCollision);
        return Part;
    };

    UStaticMesh* CubeMesh = Cube.Succeeded() ? Cube.Object.Get() : nullptr;
    UStaticMesh* SphereMesh = Sphere.Succeeded() ? Sphere.Object.Get() : CubeMesh;
    UStaticMesh* CylinderMesh = Cylinder.Succeeded() ? Cylinder.Object.Get() : CubeMesh;

    Body = AddPart(TEXT("Body"), SphereMesh, FVector(0.0f, 0.0f, 42.0f), FRotator::ZeroRotator, FVector(0.62f, 0.46f, 0.18f));
    ArmCrossA = AddPart(TEXT("ArmCrossA"), CubeMesh, FVector(0.0f, 0.0f, 42.0f), FRotator(0.0f, 45.0f, 0.0f), FVector(1.75f, 0.075f, 0.045f));
    ArmCrossB = AddPart(TEXT("ArmCrossB"), CubeMesh, FVector(0.0f, 0.0f, 42.0f), FRotator(0.0f, -45.0f, 0.0f), FVector(1.75f, 0.075f, 0.045f));

    const TArray<FVector> MotorLocations = {
        FVector(62.0f, 62.0f, 44.0f),
        FVector(62.0f, -62.0f, 44.0f),
        FVector(-62.0f, 62.0f, 44.0f),
        FVector(-62.0f, -62.0f, 44.0f)
    };
    for (int32 Index = 0; Index < MotorLocations.Num(); ++Index)
    {
        AddPart(*FString::Printf(TEXT("Motor%d"), Index), CylinderMesh, MotorLocations[Index], FRotator::ZeroRotator, FVector(0.13f, 0.13f, 0.07f));
        AddPart(
            *FString::Printf(TEXT("Rotor%d"), Index),
            CylinderMesh,
            MotorLocations[Index] + FVector(0.0f, 0.0f, 9.0f),
            FRotator::ZeroRotator,
            FVector(0.34f, 0.34f, 0.012f)
        );
    }

    AddPart(TEXT("LeftSkid"), CubeMesh, FVector(0.0f, 30.0f, 3.5f), FRotator::ZeroRotator, FVector(0.58f, 0.035f, 0.025f));
    AddPart(TEXT("RightSkid"), CubeMesh, FVector(0.0f, -30.0f, 3.5f), FRotator::ZeroRotator, FVector(0.58f, 0.035f, 0.025f));
    AddPart(TEXT("FrontLeftStrut"), CubeMesh, FVector(27.0f, 30.0f, 21.0f), FRotator::ZeroRotator, FVector(0.035f, 0.035f, 0.17f));
    AddPart(TEXT("RearLeftStrut"), CubeMesh, FVector(-27.0f, 30.0f, 21.0f), FRotator::ZeroRotator, FVector(0.035f, 0.035f, 0.17f));
    AddPart(TEXT("FrontRightStrut"), CubeMesh, FVector(27.0f, -30.0f, 21.0f), FRotator::ZeroRotator, FVector(0.035f, 0.035f, 0.17f));
    AddPart(TEXT("RearRightStrut"), CubeMesh, FVector(-27.0f, -30.0f, 21.0f), FRotator::ZeroRotator, FVector(0.035f, 0.035f, 0.17f));
    CameraPod = AddPart(TEXT("CameraPod"), SphereMesh, FVector(43.0f, 0.0f, 24.0f), FRotator::ZeroRotator, FVector(0.14f, 0.14f, 0.12f));

    CameraGimbal = CreateDefaultSubobject<USceneComponent>(TEXT("CameraGimbal"));
    CameraGimbal->SetupAttachment(DroneRoot);
    CameraGimbal->SetRelativeLocation(FVector(48.0f, 0.0f, 29.0f));

    FpvCamera = CreateDefaultSubobject<UCameraComponent>(TEXT("FpvCamera"));
    FpvCamera->SetupAttachment(CameraGimbal);
    FpvCamera->FieldOfView = 92.0f;
    FpvCamera->SetActive(true);

    SpringArm = CreateDefaultSubobject<USpringArmComponent>(TEXT("SpringArm"));
    SpringArm->SetupAttachment(DroneRoot);
    SpringArm->TargetArmLength = 450.0f;
    SpringArm->TargetOffset = FVector(0.0f, 0.0f, 48.0f);
    SpringArm->SetRelativeRotation(FRotator(-14.0f, 0.0f, 0.0f));
    SpringArm->bEnableCameraLag = true;
    SpringArm->CameraLagSpeed = 5.0f;

    ChaseCamera = CreateDefaultSubobject<UCameraComponent>(TEXT("ChaseCamera"));
    ChaseCamera->SetupAttachment(SpringArm, USpringArmComponent::SocketName);
    ChaseCamera->SetActive(false);

    TelemetryReceiver = CreateDefaultSubobject<UXStarTelemetryReceiverComponent>(TEXT("TelemetryReceiver"));
    AutoPossessPlayer = EAutoReceiveInput::Player0;
}

void AXStarDronePawn::BeginPlay()
{
    Super::BeginPlay();
    TArray<UStaticMeshComponent*> Parts;
    GetComponents<UStaticMeshComponent>(Parts);
    for (UStaticMeshComponent* Part : Parts)
    {
        UMaterialInstanceDynamic* Material = Part->CreateAndSetMaterialInstanceDynamic(0);
        if (!Material)
        {
            continue;
        }
        const FString Name = Part->GetName();
        const FLinearColor Color = Name == TEXT("Body")
            ? FLinearColor(2.5f, 0.12f, 0.005f)
            : Name.Contains(TEXT("Rotor")) || Name.Contains(TEXT("Camera"))
            ? FLinearColor(0.025f, 0.03f, 0.035f)
            : Name.Contains(TEXT("Motor"))
                ? FLinearColor(1.0f, 0.24f, 0.015f)
                : FLinearColor(0.82f, 0.84f, 0.86f);
        Material->SetVectorParameterValue(TEXT("Color"), Color);
        Material->SetVectorParameterValue(TEXT("BaseColor"), Color);
    }
    SetViewMode(TEXT("FPV"));
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
    const FVector TargetLocation(NorthM * 100.0, EastM * 100.0, FMath::Max(0.0, Telemetry.AltitudeM * 100.0));
    const FRotator TargetRotation(Telemetry.PitchDeg, Telemetry.YawDeg, Telemetry.RollDeg);
    SetActorLocationAndRotation(
        FMath::VInterpTo(GetActorLocation(), TargetLocation, DeltaSeconds, 10.0f),
        FMath::RInterpTo(GetActorRotation(), TargetRotation, DeltaSeconds, 10.0f)
    );
    CameraGimbal->SetRelativeRotation(FRotator(Telemetry.GimbalPitchDeg, 0.0f, 0.0f));
    SetViewMode(Telemetry.ViewMode);
}

void AXStarDronePawn::SetViewMode(const FString& ViewMode)
{
    const bool bUseChaseView = ViewMode == TEXT("CHASE");
    if (bUseChaseView == bChaseViewActive && FpvCamera->IsActive() != bUseChaseView)
    {
        return;
    }
    bChaseViewActive = bUseChaseView;
    FpvCamera->SetActive(!bUseChaseView);
    ChaseCamera->SetActive(bUseChaseView);
}
