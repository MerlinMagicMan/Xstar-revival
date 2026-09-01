using UnrealBuildTool;
using System.Collections.Generic;

public class XStarSimulatorTarget : TargetRules
{
    public XStarSimulatorTarget(TargetInfo Target) : base(Target)
    {
        Type = TargetType.Game;
        DefaultBuildSettings = BuildSettingsVersion.Latest;
        IncludeOrderVersion = EngineIncludeOrderVersion.Latest;
        ExtraModuleNames.Add("XStarSimulator");
    }
}
