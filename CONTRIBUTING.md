# Contributing

Pull requests on GitHub are the way in. Open an issue first for anything larger than a fix, so
the shape is agreed before the work.

- Build with `mvn -B clean verify` on Java 25. The enforcer refuses any dependency on a
  Business-edition or proprietary artifact; that boundary is deliberate.
- Sign your commits off (`git commit -s`) to certify the [Developer Certificate of
  Origin](https://developercertificate.org/). Contributions are accepted under Apache-2.0.
- Code, comments and commit messages are English. A commit message says what changed and why.
- Every source file carries the Apache-2.0 header; `mvn -Papache-license license:check` verifies it.
