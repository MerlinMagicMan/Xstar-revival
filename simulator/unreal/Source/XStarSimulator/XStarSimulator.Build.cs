using UnrealBuildTool;

public class XStarSimulator : ModuleRules
{
    public XStarSimulator(ReadOnlyTargetRules Target) : base(Target)
    {
        PCHUsage = PCHUsageMode.UseExplicitOrSharedPCHs;
        PublicDependencyModuleNames.AddRange(new[]
        {
            "Core",
            "CoreUObject",
            "Engine",
            "InputCore",
            "Json",
            "Networking",
            "Sockets"
        });
    }
}
