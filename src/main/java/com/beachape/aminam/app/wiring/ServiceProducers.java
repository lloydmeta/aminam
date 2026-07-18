package com.beachape.aminam.app.wiring;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;

@ApplicationScoped
class ServiceProducers {

  @Produces
  @ApplicationScoped
  // The single place the app's wall clock is created; UTC is intentional.
  @SuppressWarnings("TimeZoneUsage")
  Clock clock() {
    return Clock.systemUTC();
  }
}
