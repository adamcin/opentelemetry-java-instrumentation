plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.sling")
    module.set("org.apache.sling.api")
    versions.set("[2.25.0,3)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.sling:org.apache.sling.api:2.25.0")

  testLibrary("org.apache.sling:org.apache.sling.api:2.25.0")
}
