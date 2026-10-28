plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    fun plugin(dependency: Provider<PluginDependency>) = dependency.map {
        "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
    }

    implementation ( plugin( libs.plugins.spotless ) )
    implementation ( plugin( libs.plugins.shadow ) )

    // explicitly set guava version to resolve a build-dependency issue
    implementation( libs.guava )

}
