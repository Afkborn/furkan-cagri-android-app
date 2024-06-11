package com.example.arduinosensor

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ekn.gruzer.gaugelibrary.ArcGauge
import com.ekn.gruzer.gaugelibrary.HalfGauge
import com.ekn.gruzer.gaugelibrary.Range
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*


class MainActivity : Activity() {
    private var  connectedArd : Boolean = false
    private lateinit var  arcGaugeTemp : ArcGauge
    private lateinit var  arcGaugeHumidty : ArcGauge
    private lateinit var  halfGaugeWater : HalfGauge
    private lateinit var  halfGaugeFlame : HalfGauge
    private lateinit var  halfGaugeGas : HalfGauge
    private lateinit var wsStatusTextView : TextView
    private lateinit var wsArdStatusTextView : TextView

    private var highGas : Int = 700
    private var highWater : Int = 100
    private var highFlame : Int = 500



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createNotificationChannel()
        wsStatusTextView = findViewById(R.id.wsStatus)
        wsStatusTextView.setText(R.string.main_wsStat_False)


        wsArdStatusTextView = findViewById(R.id.txt_ArdStat)
        wsStatusTextView.setText(R.string.main_ArdStat_False)



        val btnRefresh : Button = findViewById(R.id.btnRefresh)
        btnRefresh.setOnClickListener {
            btnRefresh_Click()
        }

        setTempGauge()
        setHumidtyGauge()
        setWaterGauge()
        setFlameGauge()
        setGasGauge()

        getLimitData()
        getLastData()

        checkArduinoStat()


        // WebSocket bağlantısını oluştur
        try {
            val ws = WebSocketFactory().createSocket("ws://149.34.202.193:3232/")
            ws.addListener(object : WebSocketAdapter() {
                @Throws(Exception::class)
                override fun onTextMessage(websocket: WebSocket, message: String) {
                    // Gelen JSON verisini işle
                    try {
                        wsStatusTextView.setText(R.string.main_wsStat_True)
                        val json = JSONObject(message)
                        val type = json.getString("type")
                        if ("SENSOR_DATA" == type) {
                            arcGaugeTemp.value = json.getDouble("temperaute")
                            arcGaugeHumidty.value = json.getDouble("humidity")
                            halfGaugeWater.value =  json.getDouble("water")
                            halfGaugeFlame.value = json.getDouble("flame")
                            halfGaugeGas.value = json.getDouble("gas")
                        }
                        if ("GAS_HIGH".equals(type) || "FLAME_HIGH".equals(type) || "HUMIDITY_LOW".equals(type)   || "WATER_HIGH".equals(type)) {
                            showNotification(json.getString("title"), json.getString("message"));
                        }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Error parsing JSON: " + e.message)
                    }
                }

                override fun onConnected(
                    websocket: WebSocket?,
                    headers: MutableMap<String, MutableList<String>>?
                ) {
                    wsStatusTextView.setText(R.string.main_wsStat_True)
                    super.onConnected(websocket, headers)
                }

                override fun onError(websocket: WebSocket?, cause: WebSocketException?) {
                    super.onError(websocket, cause)
                    wsStatusTextView.setText(R.string.main_wsStat_False)

                }
            })
            ws.connectAsynchronously()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.channel_name)
            val description = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            val notificationManager = getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(0, builder.build())
    }

    companion object {
        private const val TAG = "WebSocketExample"
        private const val CHANNEL_ID = "TemperatureHighChannel"
    }

    private fun btnRefresh_Click(){
        Toast.makeText(this, "Güncelleniyor...", Toast.LENGTH_LONG).show()
        getLimitData()
        checkArduinoStat()


    }


    private fun checkArduinoStat(){
        runBlocking {
            val client = HttpClient(CIO)
            try {
                // GET isteği
                val url = "http://149.34.202.193:8080/sensors/arduino-status"
                val response: HttpResponse = client.get(url)
                val responseBody: String = response.bodyAsText()
                val jsonObj = JSONObject(responseBody)
                connectedArd = jsonObj.getBoolean("status")
                if (connectedArd){
                    wsArdStatusTextView.setText(R.string.main_ArdStat_True)
                }
                else{
                    wsArdStatusTextView.setText(R.string.main_ArdStat_False)
                }

            } catch (e: Exception) {
                // Hata durumunda hata mesajını yazdır
                e.printStackTrace()
                wsArdStatusTextView.setText(R.string.main_ArdStat_False)
            } finally {
                // İstemciyi kapat
                client.close()
            }
        }
    }

    private fun getLimitData(){
        runBlocking {
            val client = HttpClient(CIO)
            try {
                // GET isteği
                val url = "http://149.34.202.193:8080/sensors/limit-data"
                val response: HttpResponse = client.get(url)
                val responseBody: String = response.bodyAsText()
                val jsonObj = JSONObject(responseBody)
                val data = jsonObj.getJSONObject("data")
                highGas = data.getInt("highGas")
                highFlame = data.getInt("highFlame")
                highWater = data.getInt("highWater")

                setGasGauge()
                setFlameGauge()
                setWaterGauge()


            } catch (e: Exception) {
                // Hata durumunda hata mesajını yazdır
                e.printStackTrace()
            } finally {
                // İstemciyi kapat
                client.close()
            }
        }
    }

    private fun getLastData(){
        runBlocking {
            // Ktor client tanımlaması
            val client = HttpClient(CIO)

            try {
                // GET isteği
                val url = "http://149.34.202.193:8080/sensors/sensor_data"
                val response: HttpResponse = client.get(url)
                val responseBody: String = response.bodyAsText()
                val jsonObj = JSONObject(responseBody)
                val data = jsonObj.getJSONObject("data")
                val temp = data.getJSONObject("temperature").getDouble("value")
                val humidty = data.getJSONObject("humidity").getDouble("value")
                val gas = data.getJSONObject("gas").getDouble("value")
                val flame = data.getJSONObject("flame").getDouble("value")
                val water = data.getJSONObject("water").getDouble("value")

                arcGaugeTemp.value = temp
                arcGaugeHumidty.value = humidty
                halfGaugeWater.value = water
                halfGaugeFlame.value = flame
                halfGaugeGas.value = gas



            } catch (e: Exception) {
                // Hata durumunda hata mesajını yazdır
                e.printStackTrace()
            } finally {
                // İstemciyi kapat
                client.close()
            }
        }
    }

    private fun setGasGauge(){
        halfGaugeGas = findViewById(R.id.halfGauge_Gas)

        val range = Range()
        range.color = Color.parseColor("#8a9d9e")
        range.from = 0.0
        range.to = highGas.toDouble()

        val range1 = Range()
        range1.color = Color.parseColor("#eb3434")
        range1.from = highGas.toDouble()
        range1.to = 1000.0


        halfGaugeGas.addRange(range)
        halfGaugeGas.addRange(range1)

        halfGaugeGas.minValue = 0.0
        halfGaugeGas.maxValue = 1000.0
    }

    private fun setFlameGauge(){
        halfGaugeFlame = findViewById(R.id.halfGauge_Flame)

        val range = Range()
        range.color = Color.parseColor("#eb3434")
        range.from = 0.0
        range.to = highFlame.toDouble()

        val range1 = Range()
        range1.color = Color.parseColor("#8a9d9e")
        range1.from = highFlame.toDouble()
        range1.to = 1100.0


        halfGaugeFlame.addRange(range)
        halfGaugeFlame.addRange(range1)

        halfGaugeFlame.minValue = 0.0
        halfGaugeFlame.maxValue = 1100.0
    }

    private fun setWaterGauge(){
        halfGaugeWater = findViewById(R.id.halfGauge_water)

        val range = Range()
        range.color = Color.parseColor("#345eeb")
        range.from = 0.0
        range.to = highWater.toDouble()

        val range1 = Range()
        range1.color = Color.parseColor("#eb3434")
        range1.from =  highWater.toDouble()
        range1.to = 400.0

        halfGaugeWater.addRange(range)
        halfGaugeWater.addRange(range1)

        halfGaugeWater.minValue = 0.0
        halfGaugeWater.maxValue = 400.0

    }

    private fun setTempGauge(){
        // gauge
        arcGaugeTemp  = findViewById(R.id.arcGauge_temp)

        val range = Range()
        range.color = Color.parseColor("#345eeb")
        range.from = -10.0
        range.to = 16.0

        val range2 = Range()
        range2.color = Color.parseColor("#ebd934")
        range2.from = 16.0
        range2.to = 30.0

        val range3 = Range()
        range3.color = Color.parseColor("#eb3434")
        range3.from = 30.0
        range3.to = 50.0

        //add color ranges to gauge
        arcGaugeTemp.addRange(range)
        arcGaugeTemp.addRange(range2)
        arcGaugeTemp.addRange(range3)

        //set min max and current value
        arcGaugeTemp.minValue = -10.0
        arcGaugeTemp.maxValue = 50.0

    }

    private fun setHumidtyGauge(){
        arcGaugeHumidty = findViewById(R.id.arcGauge_humidty)

        val range = Range()
        range.color = Color.parseColor("#4290f5")
        range.from = 0.0
        range.to = 100.0

        arcGaugeTemp.addRange(range)

        arcGaugeTemp.minValue = 0.0
        arcGaugeTemp.maxValue = 100.0

    }



}