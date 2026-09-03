package com.jucasoliveira.kitchensink.architecture.fixtures.leaky.application;

/**
 * Violates {@code repository_ports_are_interfaces}: a "Repository" in the application layer that
 * is a class. ADR-0005 §2 — a port with an implementation inside it cannot have two adapters.
 */
public final class LeakyRepository {
}
