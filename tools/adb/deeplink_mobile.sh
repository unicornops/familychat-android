#! /bin/bash

# Copyright (c) 2025 Element Creations Ltd.
# Copyright 2025 New Vector Ltd.
#
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
# Please see LICENSE files in the repository root for full details.

# Format is:
# https://safechat.family/app/?account_provider=smith.safechat.family&login_hint=mxid:@alice:smith.safechat.family

adb shell am start -a android.intent.action.VIEW \
    -d "https://safechat.family/app/?account_provider=smith.safechat.family\\&login_hint=mxid:@alice:smith.safechat.family"
