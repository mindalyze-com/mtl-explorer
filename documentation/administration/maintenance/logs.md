# Logs

Open **Settings → Logs** to see what the server and browsers reported.

- **Stored events** lists errors and warnings from the server and from browsers.
  Filter by source, severity, topic, and time, then click **Refresh**. Open a row
  to see the full error.
- **Server file** shows the latest lines of the server log.

Browser problems also appear in **Server file** as short **Client problem**
entries. Open the stored event for the full details.

To turn off both viewers, set `mtl.log-viewer-enabled=false`.

## Retention

Stored events are deleted after 30 days by default:

```yaml
mtl:
  system-logs:
    retention-enabled: true
    retention: P30D
    cleanup-batch-size: 1000
    cleanup-interval: PT1H
```

If events pile up faster than they are deleted, increase `cleanup-batch-size`
or shorten `cleanup-interval`. Set `retention-enabled: false` to keep all events.

See [diagnostic logging](../../architecture/diagnostic-logging.md) for the API
and technical details.
