# rates246

Java 17 project. OpenJDK 17.0.20.1 and GNU Make 4.3 are available in WSL.

Project-local dependencies:
- third_party/commons-math3-3.6.1.jar — Apache Commons Math 3.6.1
- third_party/junit-platform-console-standalone-1.10.3.jar — JUnit Platform Console Standalone 1.10.3, includes Jupiter

Compile against the first JAR with javac -cp. The second JAR supports the JUnit console launcher. The JARs contain their license notices. Exact downloads and checksums are listed in dependencies.lock.json.
