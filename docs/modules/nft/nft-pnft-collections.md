# pNFT + collections support (compat module)

Module: `:artemis-nft-compat`

## pNFT support

### PDAs
- token record PDA: `Pdas.tokenRecordPda(mint, tokenAccount)`

### Parser
- `TokenRecordParser` extracts:
  - state
  - delegate + role (if present)
  - lockedBy (if present)
  - ruleSet (if present)

### Client helper
- `NftClient.fetchTokenRecord(mint, tokenAccount)`

## Collections support

### PDAs
- collection authority record PDA: `Pdas.collectionAuthorityRecordPda(collectionMint, authority)`

### Parser
- `CollectionAuthorityRecordParser`

### Token Metadata instruction builders
- approve collection authority record: `approveCollectionAuthority`
- revoke collection authority record: `revokeCollectionAuthority`
- set and verify collection: `setAndVerifyCollection`
- unverify collection: `unverifyCollection`
- verify sized collection item: `verifySizedCollectionItem`

### Client helper
- `NftClient.fetchCollectionAuthorityRecord(collectionMint, authority)`

## Metadata read model upgrade
`MetadataParser` now also exposes `collectionDetails` for sized collections.
