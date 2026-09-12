# ProGuard config: used ONLY as a CLDC preverifier for our compiled MIDlet.
# We don't shrink/optimize/obfuscate — just read classes, preverify (StackMap
# for CLDC), and write them back out. This replaces the old WTK `preverify`
# binary, which has no Apple-Silicon build.

-injars  /work/out/appclasses
-outjars /work/out/app-preverified.jar

-libraryjars <java.home>/lib/rt.jar
-libraryjars /work/tools/microemu-cldc.jar
-libraryjars /work/tools/microemu-midp.jar
-libraryjars /work/out/stubs.jar

-microedition
-dontshrink
-dontoptimize
-dontobfuscate
-dontusemixedcaseclassnames
-dontnote
-dontwarn

-keep class ** { *; }
