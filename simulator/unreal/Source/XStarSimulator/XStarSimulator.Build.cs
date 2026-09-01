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
            "PixelStreaming2",
            "PixelStreaming2Core",
            "PixelStreaming2Servers",
            "Sockets"
        });
    }
}
