package dipanjanmantri.buetoothle;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Adapter;

import java.util.ArrayList;


public class DeviceScanActivity extends ListActivity {

    private Handler mHandler;
    private boolean mScanning;
    private BluetoothAdapter mBluetoothAdapter;
    private LeDeviceListAdapter mLeDeviceListAdapter;

    private static final int REQUEST_ENABLE_BT=1;
    private static final long SCAN_PERIOD=10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_scan);
        getActionBar().setTitle(R.string.title_devices);
        mHandler = new Handler();

        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            Toast.makeText(this,R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }



        final BluetoothManager bluetoothManager=(BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter=bluetoothManager.getAdapter();

        if(mBluetoothAdapter==null)
        {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        if(!mScanning)
        {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);



        }else{
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_indeterminate_progress);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
           switch (item.getItemId())
           {
               case R.id.menu_scan:
                   mLeDeviceListAdapter.clear();
                   scanLeDevice(true);
                   break;
               case R.id.menu_stop:
                   scanLeDevice(false);
                   break;
           }

        return  true;
    }

    protected void onResume()
    {
        super.onResume();
        if(!mBluetoothAdapter.isEnabled())
        {
            Intent intent=new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
        }

        mLeDeviceListAdapter = new LeDeviceListAdapter();

        setListAdapter(mLeDeviceListAdapter);
        scanLeDevice(true);
    }





    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if(requestCode==REQUEST_ENABLE_BT && resultCode== Activity.RESULT_CANCELED)
        {
            finish();
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    protected void onPause()
    {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    protected void onListItemClick(ListView l, View v, int position, long id)
    {
            final BluetoothDevice device=mLeDeviceListAdapter.getDevice(position);
            if(device==null){
                return;
            }

            final Intent intent=new Intent(getApplicationContext(), DeviceScanActivity.class);
            intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
            intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
            if(mScanning){
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                mScanning=false;
            }

            startActivity(intent);
    }

    private void scanLeDevice(final boolean enable) {

        if (enable) {

            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }


    //Adapter for holding devices found through scanning
    private class LeDeviceListAdapter extends BaseAdapter{
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter(){
            super();
            mLeDevices=new ArrayList<BluetoothDevice>();
            mInflator=DeviceScanActivity.this.getLayoutInflater();
        }



        public void addDevice(BluetoothDevice device)
        {
            if(!mLeDevices.contains(device))

            {
                mLeDevices.add(device);
            }


        }

        public BluetoothDevice getDevice(int position)
        {
            return mLeDevices.get(position);
        }

        public void clear()
        {
            mLeDevices.clear();
        }


        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {

            ViewHolder viewHolder;
            //Serves the purpose of general listview
            if(view==null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) findViewById(R.id.device_name);
                view.setTag(viewHolder);
            }
            else{
                viewHolder=(ViewHolder)view.getTag();

            }

            BluetoothDevice device=mLeDevices.get(i);
            final String deviceName=device.getName();
            if(deviceName!=null && deviceName.length()>0){
                viewHolder.deviceName.setText(deviceName);

            }else{
                viewHolder.deviceName.setText(R.string.unknown_device);

            }

            viewHolder.deviceAddress.setText(device.getAddress());



            return view;
        }
    }


    private BluetoothAdapter.LeScanCallback mLeScanCallback=new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice bluetoothDevice, int i, byte[] bytes) {


            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLeDeviceListAdapter.addDevice(bluetoothDevice);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    static class ViewHolder{
        TextView deviceName;
        TextView deviceAddress;
    }








}
