# MWA batching

Some wallets set limits like `max_transactions_per_request`.

Artemis reads `get_capabilities` and automatically splits into batches.

## Behavior

- sign_transactions: split by max_messages_per_request or max_transactions_per_request
- sign_and_send_transactions: split by max_transactions_per_request
- signThenSendViaRpc: same batching, with wallet-broadcast preferred

Each batch uses a tiny retry (default 2 attempts) to smooth out transient wallet failures.
