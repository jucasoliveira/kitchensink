# ADR-0004 — Async order workflow: in-process transactional events

- **Status:** **Deferred, unbuilt** — see [ADR-0006](0006-deliverable-scope-kitchensink-slice.md).
  The decision below is not reversed; the order workflow is simply out of the delivered scope
  (tier T3). This ADR is the design that would be implemented on resumption, and the answer to
  "how would you have done the async part?" in the playback.
- **Date:** 2026-09-01

## Context

The legacy order workflow is 8 MDBs wired through 6 queues and **one topic**, carrying XML
documents validated against DTDs. The topic matters: `InvoiceMDB` and `MailInvoiceMDB` are two
independent subscribers, so publish/subscribe fan-out is load-bearing behaviour, not an
implementation detail.

## Options

| Option | Cost | Fidelity |
| --- | --- | --- |
| A. Embedded ActiveMQ Artemis + JMS | ~4h (broker config, destinations, listeners, test harness) | Highest — real queues/topics, real redelivery |
| B. Kafka/RabbitMQ in Compose | ~6h + infra in the demo | High, but heaviest |
| C. **Spring application events, published transactionally** | ~1h | Preserves ordering, fan-out and at-least-once semantics; loses the process boundary and the wire format |

## Decision

**Option C.** Domain events published with `ApplicationEventPublisher` and consumed by
`@TransactionalEventListener(phase = AFTER_COMMIT)` handlers. Multiple listeners on
`InvoiceIssued` reproduce the topic fan-out exactly. If a compatible **Spring Modulith** release
exists for Boot 4.1, use its event publication registry for persisted, retryable delivery;
otherwise plain framework events plus an outbox-shaped `event_publication` collection.

Event types replace the XML documents; the DTD validation step becomes compile-time typing plus
Bean Validation — that is the honest "what did modernisation actually buy you" answer.

## Consequences

- **We are explicit that this collapses four processes into one.** The events are named and
  shaped so that promoting any listener to a real broker consumer is a configuration and
  packaging change (Modulith's `@Externalized`), not a redesign. Say this out loud in the
  playback rather than letting it be discovered.
- Cross-process retry/DLQ semantics are simulated, not real. Documented in the README's
  "known gaps" section.
- Saved budget goes to the MongoDB adapter (ADR-0005), which is worth far more to this audience
  than a broker container.

## Rule preserved verbatim

Auto-approve below **$500 (US) / ¥50 000 (JP)** — `PurchaseOrderMDB.java:183` `canIApprove`.
Above threshold the order parks in `PENDING` until an admin acts. This is the demo's dramatic
moment and is covered by tests in both currencies.
