# Security policy

Do not include document contents, absolute workspace paths, databases, or credentials in public reports.
For a suspected vulnerability, use the repository's private security advisory channel when available.
Until a private contact is configured, report only a minimal redacted description and request a private channel.

FilePilot is a single-user loopback application, not a sandbox against malicious software running as the same OS user.
See [security model](docs/SECURITY_MODEL.md). Never expose the port through a proxy, tunnel or port forwarding.
