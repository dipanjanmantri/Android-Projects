package dipanjanmantri.buetoothle;

import java.security.Provider;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

/**
 * Created by Diphanjan on 8/24/2016.
 */
//The BluetoothService class extends the Service class to manage connection and data communication
// with a GATT Server hosted on a given bluetooth device
public class BluetoothLeService extends Service{

    private final static String TAG=BluetoothLeService.class.getSimpleName();
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;


    private static final int STATE_DISCONNECTED=0;
    private static final int STATE_CONNECTING=1;
    private static final int STATE_CONNECTED=2;

    private int mConnectionState = STATE_DISCONNECTED;

    public final static String ACTION_GATT_CONNECTED="ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED="ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED="ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE="ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA="EXTRA_DATA";


    public final static UUID UUID_HEART_RATE_MEASUREMENT=UUID.fromString(SimpleGattAttributes.HEART_RATE_MEASUREMENT);


    //The following code implements various callback methods for GATT events, connection change and services discovererd
    private final BluetoothGattCallback mGattCallback=new BluetoothGattCallback(){


        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if(newState==BluetoothProfile.STATE_CONNECTED){
                intentAction=ACTION_GATT_CONNECTED;
                mConnectionState=STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "CONNECTED TO GATT SERVER");
                //attempts to discover services after successful connection
                Log.i(TAG, "attempting to start services discovery:"+mBluetoothGatt.discoverServices());

            }else if(newState==BluetoothProfile.STATE_DISCONNECTED){
                intentAction=ACTION_GATT_DISCONNECTED;
                mConnectionState=STATE_DISCONNECTED;
                Log.i(TAG, "DISCONNECTED FROM GATT SERVER");
                broadcastUpdate(intentAction);
            }



        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if(status==BluetoothGatt.GATT_SUCCESS){
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);

            }else{
                Log.w(TAG, "onServicesDiscovered received:"+status);

            }

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if(status==BluetoothGatt.GATT_SUCCESS){
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {


            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);

        }
    };

    private void broadcastUpdate(final String action){
        final Intent intent=new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic)
    {

        final Intent intent=new Intent(action);
        //The following code is a special handling for heart rate profile and
        // data parsing is done based on different profile specifications

        if(UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())){

            int flag=characteristic.getProperties();
            int format=-1;
            if((flag & 0x01)!=0){
                format=BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "HEART RATE FORMAT UINT16");

            }else{
                format=BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8");

            }

            final int heartRate=characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate:" +heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));

        }else{
            // For all other profiles writes the data formatted in HEX
            final byte[] data=characteristic.getValue();
            if(data!=null && data.length>0){
                final StringBuilder stringBuilder=new StringBuilder(data.length);
                for(byte byteChar: data)
                {
                    stringBuilder.append(String.format("%02X ", byteChar));

                }

                intent.putExtra(EXTRA_DATA, new String(data)+"\n"+stringBuilder.toString());
            }
        }

        sendBroadcast(intent);
    }





    public class LocalBinder extends Binder{
        BluetoothLeService getService(){
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent){

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent){

        //The following code calls the BluetoothGatt.close() is called to release the resources.
        //The close() method is invoked when UI is disconnected from the Service.

        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder=new LocalBinder();

    //This initialize a reference to a BluetoothAdapter through BluetoothManager

    public boolean initialize()
    {

        if(mBluetoothManager==null){
            mBluetoothManager=(BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
            if(mBluetoothManager==null){
                Log.e(TAG, "Unable to initialize the BluetoothManager");
                return false;
            }
        }

        mBluetoothAdapter=mBluetoothManager.getAdapter();
        if(mBluetoothAdapter==null){
            Log.e(TAG, "Unable to obtain a BluetoothAdapter");
            return false;
        }

        return true;
    }

    //The following code connects to the GATT server hosted on BluetoothLeDevice
    //@param address, The device address of the destination device
    //@return Return true for successful connection initialization
    //The connection result is reported asynchronously thorugh the BluetoothGattCallback onConnectionStateChange

    public boolean connect(final String address){
        if(mBluetoothAdapter==null || address==null){
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified");
            return false;
        }

        //Trying to reconnect to a previously connected device
        if(mBluetoothDeviceAddress!=null && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt!=null)
        {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection");
            if(mBluetoothGatt.connect()){
                mConnectionState=STATE_CONNECTING;
                return true;
            }

            else{
                return false;
            }
        }


        final BluetoothDevice device=mBluetoothAdapter.getRemoteDevice(address);
        if(device==null){
            Log.w(TAG, "device not found, unable to connect");
            return false;
        }

        //This for a manually extablishing connection by setting the autoConnect parameter to false
        mBluetoothGatt=device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection");
        mBluetoothDeviceAddress=address;
        mConnectionState=STATE_CONNECTING;
        return true;

    }

    //This disconnects an existing connection or cancels a pending connection
    //The disconnection result is reported asynchronously through the BluetoothCallback.onConnectionStateChange callback

    public void disconnect()
    {
        if(mBluetoothAdapter==null || mBluetoothGatt==null){
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        mBluetoothGatt.disconnect();

    }
    //The app must invoke this method to release the resources

    public void close()
    {
        if(mBluetoothGatt==null)
        {
            return;
        }

        mBluetoothGatt.close();
        mBluetoothGatt=null;
    }

    //This code requests a read on a given BluetoothCharacteristic. The read result is reported asyncronously through
    // BluetoothGattCallback onCharacteristicRead callback

    public void readCharacteristic(BluetoothGattCharacteristic characteristic)
    {
        if(mBluetoothAdapter==null || mBluetoothGatt==null)
        {
                Log.w(TAG, "BluetoothAdapter not initialized");
                return;
        }

        mBluetoothGatt.readCharacteristic(characteristic);
    }

    //Enables or disables notification on a given characteristic

    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled)
    {

        if(mBluetoothAdapter==null || mBluetoothGatt==null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        //This is specific to heart rate measurement

        if(UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())){

            BluetoothGattDescriptor descriptor=characteristic.getDescriptor
                    (UUID.fromString(SimpleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));

            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);

        }
    }

    //This retrieves a list of supported GATT services on the connected device
    //This should be invoked only after BluetoothGatt.discoverServices() completes successfully
    //Return a list of supported services

    public List<BluetoothGattService> getSupportedGattServices()
    {

        if(mBluetoothGatt==null){
            return  null;
        }

        return mBluetoothGatt.getServices();

    }





}
