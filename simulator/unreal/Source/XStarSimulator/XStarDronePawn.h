#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Pawn.h"
#include "XStarTelemetryReceiverComponent.h"
#include "XStarDronePawn.generated.h"

class UCameraComponent;
class USceneComponent;
class USpringArmComponent;
class UStaticMeshComponent;

UCLASS()
class XSTARSIMULATOR_API AXStarDronePawn : public APawn
{
    GENERATED_BODY()

public:
    AXStarDronePawn();
    virtual void BeginPlay() override;
    virtual void Tick(float DeltaSeconds) override;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) UXStarTelemetryReceiverComponent* TelemetryReceiver;
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) USceneComponent* DroneRoot;
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) UStaticMeshComponent* Body;
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) UStaticMeshComponent* ArmCrossA;
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) UStaticMeshComponent* ArmCrossB;
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) UStaticMeshComponent* CameraPod;
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) USceneComponent* CameraGimbal;
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) UCameraComponent* FpvCamera;
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) USpringArmComponent* SpringArm;
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) UCameraComponent* ChaseCamera;

private:
    void SetViewMode(const FString& ViewMode);

    bool bHasTelemetryOrigin = false;
    bool bChaseViewActive = false;
    double OriginLatitudeDeg = 0.0;
    double OriginLongitudeDeg = 0.0;
};
