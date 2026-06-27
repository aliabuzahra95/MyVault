# Commercial release hardening is intentionally conservative.
# Keep this file available for SDK-specific rules if future release builds expose
# reflection or native-SDK shrinker issues.

# Optional Reactor integrations referenced by transitive SDK service metadata.
-dontwarn io.micrometer.context.ContextAccessor
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
