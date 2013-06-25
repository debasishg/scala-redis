resolvers += "web-plugin.repo" at "http://siasia.github.com/maven2"    

addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "0.3.0")

resolvers += Resolver.url("sbt-plugin-releases", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.jsuereth" % "xsbt-gpg-plugin" % "0.6")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.2.0")
