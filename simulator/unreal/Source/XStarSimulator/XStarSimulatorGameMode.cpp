#include "XStarSimulatorGameMode.h"
#include "XStarDronePawn.h"

AXStarSimulatorGameMode::AXStarSimulatorGameMode()
{
    DefaultPawnClass = AXStarDronePawn::StaticClass();
}
