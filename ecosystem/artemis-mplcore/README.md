# artemis-mplcore

MPL Core (v2 lane) primitives for Kotlin apps.

## What you get

- Create flows for collections and assets
- Authority and metadata updates
- Collection membership operations
- Marketplace safety rails (lock and unlock)
- Pragmatic plugin helpers for royalties and attributes
- Raw instruction helper for custom builds

## Usage

### Create a collection

```kotlin
val ix = MplCoreInstructions.createCollection(
  collection = collectionPubkey,
  payer = payer,
  authority = authority,
  args = MplCoreArgs.CreateCollectionArgs(
    name = "My Collection",
    uri = "https://example.com/collection.json",
    updateAuthority = authority
  )
)
```

### Add royalties and attributes

```kotlin
val royaltiesIx = MplCoreInstructions.setRoyalties(
  asset = assetPubkey,
  authority = authority,
  royalties = MplCorePlugins.Royalties(basisPoints = 500)
)

val attrsIx = MplCoreInstructions.setAttributes(
  asset = assetPubkey,
  authority = authority,
  attributes = MplCorePlugins.Attributes(
    items = listOf(MplCorePlugins.Attribute("rarity", "legendary"))
  )
)
```

