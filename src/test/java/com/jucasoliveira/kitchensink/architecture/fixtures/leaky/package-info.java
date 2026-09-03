/**
 * Issue 1.10 — classes that break the architecture rules <em>on purpose</em>.
 *
 * <p>Every class under here violates exactly one rule in {@code LayeringRulesTest}, and
 * {@code RulesCanFailTest} evaluates the real rules against this package and asserts that they
 * report it. That is the ArchUnit half of "a slice-shaped test can fail for the right reason": a
 * gate that has only ever been seen green has not been proven, it has been observed.
 *
 * <p>These are test sources, and the production rules import with
 * {@code ImportOption.DoNotIncludeTests}, so nothing here is ever seen by the gates that guard
 * {@code src/main}. The package names mirror the layering vocabulary ({@code domain},
 * {@code application}, {@code adapter.web}, {@code adapter.persistence.mongo}) because the rules
 * match on those segments anywhere in a package path; the {@code leaky} prefix is what keeps them
 * out of any real bounded context.
 */
package com.jucasoliveira.kitchensink.architecture.fixtures.leaky;
