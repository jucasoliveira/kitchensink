# Legacy runtime — Java Pet Store 1.3.1_02 on its original stack

Runs the **unmodified** 2003 application on the J2EE 1.3.1 Reference Implementation,
inside a `linux/amd64` container. On Apple Silicon this runs under emulation.

Its only job is to produce a **behavioural baseline** to test the migrated app against.

## Why a container

The app requires J2SE 1.4.1 and the J2EE SDK 1.3.1 RI. Neither exists for macOS, let alone
arm64, and every module ships RI-proprietary `sun-j2ee-ri.xml` descriptors that no other
container has ever read. A pinned amd64 image is the only way to make this reproducible.

## Prerequisites

Docker with amd64 emulation. Verify with:

```bash
docker run --rm --platform linux/amd64 alpine:3 uname -m
```

That must print `x86_64`.

## Step 1 — supply the Sun binaries

**These are licensed and are not in this repository.** You have to download them yourself and
drop them in `vendor/`, which is gitignored:

| File | Where |
| --- | --- |
| `j2sdkee-1_3_1-linux.tar.gz` | Oracle Java archive — free account and licence acceptance required |
| `j2sdk-1_4_2_19-linux-i586.bin` (or the `.tar.gz`) | Oracle Java archive, same |

Record the SHA-256 of whatever you download in `vendor/CHECKSUMS` so the build is reproducible:

```bash
shasum -a 256 vendor/* | tee vendor/CHECKSUMS
```

If the J2EE SDK 1.3.1 runtime is no longer served, stop here and use the JBoss fallback
described in `docs/02-running-the-legacy-app.md` instead — do not spend hours hunting mirrors.

## Step 2 — build

Run from the **repository root**, because the build context needs `petstore1.3.1_02/`:

```bash
docker build --platform linux/amd64 -f legacy-runtime/Containerfile -t petstore-legacy:1.3.1_02 .
```

## Step 3 — run

```bash
docker run --rm -it -p 8000:8000 -p 1050:1050 --name petstore-legacy petstore-legacy:1.3.1_02
```

First boot creates the JMS queues, database resources and users, then deploys all four EARs.
It is slow under emulation — several minutes is normal.

## Step 4 — seed the catalog, then browse

```bash
curl http://localhost:8000/petstore/populate
```

Then open <http://localhost:8000/petstore/>.

| Endpoint | What it is |
| --- | --- |
| `http://localhost:8000/petstore/` | Storefront |
| `http://localhost:8000/opc/` | Order Processing Centre |
| `http://localhost:8000/supplier/` | Supplier / warehouse |
| `http://localhost:8000/admin/` | Admin console (launches the Swing client via Web Start) |

## Capturing the baseline

The point of all this. Once it is up:

1. Walk the golden paths — browse, add to cart, create an account, sign on, check out, view
   order status — and save the rendered HTML for each screen.
2. Order **below** $500 and **above** $500 to exercise both branches of `canIApprove`.
3. Dump the catalog tables and the CMP-generated order tables.
4. Capture the XML payloads moving between the four apps.

Those become the fixtures the migrated application is tested against. Without them,
"functional equivalence" is just an assertion.
