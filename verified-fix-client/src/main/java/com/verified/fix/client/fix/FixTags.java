package com.verified.fix.client.fix;

/** Custom FIX tag numbers used between the client and the server. */
public final class FixTags {

    /** VendorCode: which vendor adapter handles the request. */
    public static final int VENDOR_CODE = 9001;

    /** PoolID: which pool the request is for. */
    public static final int POOL_ID = 9002;

    private FixTags() { }
}
