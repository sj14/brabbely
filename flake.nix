{
  description = "Podcast app for Android – development environment";

  # Same nixpkgs revision as the system flake, so shared packages (JDK, …)
  # are already in the store and not downloaded twice.
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/f68407657ad294471eb4b2e65d193df26edf729f";

  outputs =
    { nixpkgs, ... }:
    let
      system = "x86_64-linux";

      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      # Must match buildToolsVersion in app/build.gradle.kts.
      buildToolsVersion = "37.0.0";

      androidComposition = pkgs.androidenv.composeAndroidPackages {
        # 37.0 to compile against; 36 only for its emulator image,
        # since there is no plain (non-Google) image for 37.0 yet.
        platformVersions = [
          "36"
          "37.0"
        ];
        buildToolsVersions = [ buildToolsVersion ];
        # The deprecated "tools" package is not needed and would pull in JDK 8.
        toolsVersion = null;
        includeEmulator = true;
        includeSystemImages = true;
        systemImageTypes = [ "default" ];
        abiVersions = [ "x86_64" ];
      };

      androidSdk = androidComposition.androidsdk;
      sdkRoot = "${androidSdk}/libexec/android-sdk";
      # The same JDK the Android cmdline-tools use, so only one JDK is downloaded.
      jdk = pkgs.jdk17;
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          androidSdk
          jdk
        ];

        ANDROID_HOME = sdkRoot;
        ANDROID_SDK_ROOT = sdkRoot;
        JAVA_HOME = jdk.home;

        # Gradle downloads its own aapt2 binary, which does not run on NixOS
        # without help. Use the one from the Nix SDK instead.
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildToolsVersion}/aapt2";
      };
    };
}
