# ashhook

[ashhook][ashhook] is a tiny microservice that will observe your IMAP mail server and forward new messages to a Webhook.
It was designed to forward mails a Discord channel.

## Development

```bash
sbt assembly
java -jar target/*/ashhook.jar
```

Required environment variables:

```bash
export IMAP_HOSTNAME="<hostname>"
export IMAP_PASSWORD="<password>"
export IMAP_USERNAME="<username>"
export IMAP_PORT=993
export WEBHOOK_URL="https://discord.com/api/webhooks/..."
```

## Via proxy

```bash
java -Dmail.imaps.proxy.host=127.0.0.1 \
  -Dmail.imaps.proxy.port=8118 \
  -jar target/*/ashhook.jar
```

## Author

\- [Oto Brglez][ob]

[ashhook]: https://github.com/ogrodje/ashhook

[ob]: https://github.com/otobrglez