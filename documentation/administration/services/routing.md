# Route planning service

Route planning uses BRouter, which starts with MTL Explorer. Its routing data is
saved in `./data/brouter-segments/`. Keep this folder when you update.

If route planning does not work, check the BRouter log:

```bash
docker compose logs -f brouter
```
