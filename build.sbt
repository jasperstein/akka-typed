val akkaVersion = "2.3.9"
val sprayVersion = "1.3.3"

// compile-time dependencies
val akkaActor                = "com.typesafe.akka"       %% "akka-actor"                    % akkaVersion
val akkaTyped                = "com.typesafe.akka"       %% "akka-typed-experimental"       % "2.4-M1"

val sprayCan                 = "io.spray"                %% "spray-can"                     % sprayVersion
val sprayRouting             = "io.spray"                %% "spray-routing"                 % sprayVersion
val sprayJson                = "io.spray"                %% "spray-json"                    % "1.3.2"

// test dependencies
val akkaTestkit              = "com.typesafe.akka"       %% "akka-testkit"                  % akkaVersion
val sprayTestkit             = "io.spray"                %% "spray-testkit"                 % sprayVersion
val scalatest                = "org.scalatest"           %% "scalatest"                     % "2.2.4" % "test"

name := """wehkamp-akka-typed"""

version := "1.0"

scalaVersion := "2.11.6"

mainClass in Compile := Some("App")

libraryDependencies ++= Seq(
	akkaActor,
	sprayCan,
	sprayRouting,
	sprayJson,
	akkaTyped,
	scalatest
)
