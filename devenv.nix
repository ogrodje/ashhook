{ pkgs, lib, config, inputs, ... }:

{
  env.ASHHOOK = "development";

  packages = [ 
	pkgs.aria2	
  ];

  languages.scala = {
  	enable = true;
	package = pkgs.scala_3;
	sbt.enable = true;
  };

  enterShell = ''
  	echo "~~~ ashhook ~~~"
	type javac && type scalac
	echo "JAVA_HOME=$JAVA_HOME"
  '';

  enterTest = ''
  	sbt test
  '';
}
