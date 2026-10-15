# Database diagram assets

The [database relation guide](../../../documentation/architecture/database.md)
uses these six diagrams. The `.mmd` files are the editable Mermaid sources;
the PNG files are rendered previews. They describe schema metadata through
Liquibase change `111` and contain no record data. Changes `110` and `111`
keep the illustrated tables, keys and relationships unchanged.

After schema changes, update the affected source and render its PNG with
Mermaid CLI. For example:

```sh
mmdc -i docs/assets/database/tracks.mmd -o docs/assets/database/tracks.png \
  -c docs/assets/database/mermaid-config.json -b white -s 2
```

Check primary keys, composite unique keys, nullable references, and every
foreign key against the Liquibase changelog. Keep application links without
foreign keys in the guide's notes. Regenerate and check the public documentation
using the [website workflow](../../../documentation/development/website/generation.md).
