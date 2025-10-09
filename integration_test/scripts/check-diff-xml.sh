#!/bin/bash

set -e
set -o pipefail

IGNORE_TRANSACTION_AND_IMPLEMENTATION='.NativeDicomModel.DicomAttribute[] | select(."+@keyword" != "ImplementationVersionName" and ."+@keyword" != "TransactionUID" and ."+@keyword" != "InstanceAvailability" and ."+@keyword" != "RetrieveURL")'

diff \
	<(yq -oj "$IGNORE_TRANSACTION_AND_IMPLEMENTATION"  $1) \
	<(yq -oj "$IGNORE_TRANSACTION_AND_IMPLEMENTATION"  $2)
