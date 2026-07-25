package android.content;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ICancellationSignal;
import android.os.IInterface;
import android.os.RemoteException;

public interface IContentProvider extends IInterface {
    Cursor query(
            AttributionSource attributionSource,
            Uri uri,
            String[] projection,
            Bundle queryArgs,
            ICancellationSignal cancellationSignal) throws RemoteException;
}
