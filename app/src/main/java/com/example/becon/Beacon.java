package com.example.becon;

import android.annotation.SuppressLint;
import android.util.Log;

/**
 * Simple model class to house relevant data coming from
 * beacon.
 */
public class Beacon {

    public String deviceAddress;
    public String id;
    private int latestRssi;

    public float battery;
    public float temperature;
    public String url;
    public String serviceUuid;


    Beacon()
    {
        this.battery = -1f;
        this.temperature = -1f;
        this.url = "url";
        this.serviceUuid = "serviceUuid";
    }

    public void update(String address, int rssi,String instanceId) {
        this.deviceAddress = address;
        this.latestRssi = rssi;
        this.id=instanceId;
    }

    @SuppressLint("DefaultLocale")
    @Override
    public String toString() {
        return String.format("InstanceID: %s\n" +
                        "Power: %ddBm\n" +
                        "Battery: %s\n" +
                        "Temperature: %s\n" +
                        "URL: %s\n" +
                        "ServiceUuid: %s\n" +
                        "Distance: %f m",
                id,
                latestRssi,
                battery < 0f ? "N/A" : String.format("%.1fV", battery),
                temperature < 0f ? "N/A" : String.format("%.1fC", temperature),
                url,
                serviceUuid,
                calculateDistance(-69, latestRssi)); //the beacon power at 1 m ranges from -65 to -72 , so the average 69
    }

    // Parse the instance id out of a UID packet
    public static String getInstanceId(byte[] data) {
        StringBuilder sb = new StringBuilder();

        //UID packets are always 18 bytes in length
        //Parse out the last 6 bytes for the id
        int packetLength = 18;
        int offset = packetLength - 6;
        for (int i=offset; i < packetLength; i++) {
            sb.append(Integer.toHexString(data[i] & 0xFF));
        }

        return sb.toString();
    }

    // Parse the battery level out of a TLM packet
    public static float getTlmBattery(byte[] data) {

        int voltage = (data[2] & 0xFF) << 8;
        voltage += (data[3] & 0xFF);

        //Value is 1mV per bit
        return voltage / 1000f;
    }

    // Parse the temperature out of a TLM packet
    public static float getTlmTemperature(byte[] data) {


        int temp = (data[4] << 8);
        temp += (data[5] & 0xFF);

        return temp / 256f;
    }

    // Calculate an estimation of the distance of the beacon and the smartphone
    private double calculateDistance(float txPower, double rssi) {

        /*
         * RSSI = TxPower - 10 * n * lg(d)
         * n = 2 (in free space)
         *
         * d = 10 ^ ((TxPower - RSSI) / (10 * n))
         */

        return Math.pow(10d, ((double) txPower - rssi) / (10 * 2));
    }
}
