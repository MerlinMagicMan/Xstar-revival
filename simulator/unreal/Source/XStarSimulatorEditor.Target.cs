using UnrealBuildTool;
using System.Collections.Generic;

public class XStarSimulatorEditorTarget : TargetRules
{
    public XStarSimulatorEditorTarget(TargetInfo Target) : base(Target)
    {
        Type = TargetType.Editor;
        DefaultBuildSettings = BuildSettingsVersion.Latest;
        IncludeOrderVersion = EngineIncludeOrderVersion.Latest;
        ExtraModuleNames.Add("XStarSimulator");
    }
}
