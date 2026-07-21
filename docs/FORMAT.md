# PackDroid formats

## Complete ZIP

```text
Example-full.zip
├─ packdroid.manifest.json
└─ mods/
   ├─ sodium.jar
   └─ fabric-api.jar
```

Every selected mod is embedded. Duplicate filenames are renamed with `_2`, `_3`, and so on.

## PackDroid Manifest (`.pdpack`)

`.pdpack` is a ZIP archive containing metadata only.

```text
Example.pdpack
├─ packdroid.manifest.json
└─ README.txt
```

The manifest stores:

- Minecraft and loader versions
- provider (`modrinth` or `local`)
- Modrinth project ID and version ID
- original filename
- download URL
- SHA-1 and SHA-512
- file size
- client/server environment

Local files have metadata and hashes but no downloadable provider URL.

## Modrinth (`.mrpack`)

```text
Example.mrpack
├─ modrinth.index.json
└─ overrides/
   └─ mods/
      └─ local-only.jar
```

Modrinth-hosted files are listed in `modrinth.index.json`. Local files, which have no permitted download URL, are embedded under `overrides/mods/`.
