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
    virtual void Tick(float DeltaSeconds) override;

    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) UXStarTelemetryReceiverComponent* TelemetryReceiver;
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) USceneComponent* DroneRoot;
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) UStaticMeshComponent* Body;
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) USceneComponent* CameraGimbal;
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) USpringArmComponent* SpringArm;
    UPROPERTY(VisibleAnywhere, BlueprintReadOnly) UCameraComponent* ChaseCamera;

private:
    bool bHasTelemetryOrigin = false;
    double OriginLatitudeDeg = 0.0;
    double OriginLongitudeDeg = 0.0;
};
