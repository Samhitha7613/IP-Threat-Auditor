package com.example.hackathon

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder

data class AbuseIpDbData(val rawJson: String, val parsed: AbuseIpDbResult)

data class AbuseIpDbResult(
    val ipAddress: String,
    val abuseConfidenceScore: Int,
    val country: String,
    val city: String,
    val isp: String,
    val usageType: String,
    val asn: Int,
    val hostname: String,
    val domain: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var domainInput: EditText
    private lateinit var checkIpButton: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var detailsCard: MaterialCardView
    private lateinit var ipAddressText: TextView
    private lateinit var confidenceScoreText: TextView
    private lateinit var ispText: TextView
    private lateinit var locationText: TextView
    private lateinit var usageTypeLayout: LinearLayout
    private lateinit var usageTypeText: TextView
    private lateinit var asnLayout: LinearLayout
    private lateinit var asnText: TextView
    private lateinit var hostnameLayout: LinearLayout
    private lateinit var hostnameText: TextView
    private lateinit var domainLayout: LinearLayout
    private lateinit var domainText: TextView

    private lateinit var geminiCard: MaterialCardView
    private lateinit var geminiSummaryText: TextView

    private val GEMINI_API_KEY = "AIzaSyClsZHopV3kzYjzXOEIib6VbFekf_M_p7g"
    private val ABUSE_IP_API_KEY = "125f48c3e47b42dd5d6fe6d8e0fe15c3baaaacbf735a8e03b7d75acd98f424abb68ff28ea71ecaa2"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeViews()

        // 🔹 Button to check IP of entered domain
        checkIpButton.setOnClickListener {
            val input = domainInput.text.toString().trim()
            if (input.isNotEmpty()) {
                lifecycleScope.launch {
                    showLoading(true)
                    processDomain(input)
                }
            } else {
                Toast.makeText(this, "Please enter a domain name", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🌐 Main processing: Resolve domain → Get IP → AbuseIPDB lookup
    private suspend fun processDomain(domain: String) {
        val ipAddress = resolveDomainToIp(domain)
        if (ipAddress != null) {
            val abuseData = getAbuseIpData(ipAddress, ABUSE_IP_API_KEY)
            if (abuseData != null) {
                updateDetailsCard(abuseData.parsed)
                detailsCard.visibility = View.VISIBLE

                val summary = getGeminiSummary(abuseData.rawJson)
                geminiSummaryText.text = summary
                geminiCard.visibility = View.VISIBLE
            } else {
                showError("Could not retrieve IP data from AbuseIPDB.")
            }
        } else {
            showError("Could not resolve domain to IP.")
        }
        showLoading(false)
    }

    // 🔹 DNS Lookup (resolve domain → IP)
    private suspend fun resolveDomainToIp(domain: String): String? = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getByName(domain)
            address.hostAddress
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 🔹 Initialize views
    private fun initializeViews() {
        domainInput = findViewById(R.id.ipAddress)
        checkIpButton = findViewById(R.id.submitButton)
        progressBar = findViewById(R.id.progressBar)

        detailsCard = findViewById(R.id.detailsCard)
        ipAddressText = findViewById(R.id.ipAddressText)
        confidenceScoreText = findViewById(R.id.confidenceScoreText)
        ispText = findViewById(R.id.ispText)
        locationText = findViewById(R.id.locationText)
        usageTypeLayout = findViewById(R.id.usageTypeLayout)
        usageTypeText = findViewById(R.id.usageTypeText)
        asnLayout = findViewById(R.id.asnLayout)
        asnText = findViewById(R.id.asnText)
        hostnameLayout = findViewById(R.id.hostnameLayout)
        hostnameText = findViewById(R.id.hostnameText)
        domainLayout = findViewById(R.id.domainLayout)
        domainText = findViewById(R.id.domainText)

        geminiCard = findViewById(R.id.geminiCard)
        geminiSummaryText = findViewById(R.id.geminiSummaryText)
    }

    // 🔹 Loading UI
    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        checkIpButton.isEnabled = !isLoading
        if (isLoading) {
            detailsCard.visibility = View.GONE
            geminiCard.visibility = View.GONE
        }
    }

    // 🔹 Show error message
    private fun showError(message: String) {
        geminiCard.visibility = View.VISIBLE
        geminiSummaryText.text = message
        detailsCard.visibility = View.GONE
    }

    // 🔹 Update UI with AbuseIPDB results
    private fun updateDetailsCard(result: AbuseIpDbResult) {
        val score = result.abuseConfidenceScore
        val (bgColor, borderColor) = when {
            score < 20 -> Pair(Color.parseColor("#E8F5E9"), Color.parseColor("#388E3C"))
            score in 20..60 -> Pair(Color.parseColor("#FFFDE7"), Color.parseColor("#FBC02D"))
            else -> Pair(Color.parseColor("#FFEBEE"), Color.parseColor("#D32F2F"))
        }

        detailsCard.setCardBackgroundColor(bgColor)
        detailsCard.strokeColor = borderColor

        ipAddressText.text = result.ipAddress
        confidenceScoreText.text = "${score}%"
        ispText.text = result.isp

        val location = if (result.city.isNotEmpty()) "${result.city}, ${result.country}" else result.country
        locationText.text = location

        updateOptionalField(usageTypeLayout, usageTypeText, result.usageType)
        updateOptionalField(asnLayout, asnText, if (result.asn != 0) "AS${result.asn}" else "")
        updateOptionalField(hostnameLayout, hostnameText, result.hostname)
        updateOptionalField(domainLayout, domainText, result.domain)
    }

    private fun updateOptionalField(layout: LinearLayout, textView: TextView, value: String?) {
        if (!value.isNullOrEmpty()) {
            layout.visibility = View.VISIBLE
            textView.text = value
        } else {
            layout.visibility = View.GONE
        }
    }

    // 🔹 AbuseIPDB API request
    private suspend fun getAbuseIpData(ipAddress: String, apiKey: String): AbuseIpDbData? =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val urlString = "https://api.abuseipdb.com/api/v2/check?ipAddress=${URLEncoder.encode(ipAddress, "UTF-8")}"
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Key", apiKey)
                connection.setRequestProperty("Accept", "application/json")

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val rawJson = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                    val data = JSONObject(rawJson).getJSONObject("data")
                    val parsed = AbuseIpDbResult(
                        ipAddress = data.getString("ipAddress"),
                        abuseConfidenceScore = data.getInt("abuseConfidenceScore"),
                        country = data.optString("countryCode", ""),
                        city = data.optString("city", ""),
                        isp = data.optString("isp", ""),
                        usageType = data.optString("usageType", ""),
                        asn = data.optInt("asn", 0),
                        hostname = data.optString("hostnames", ""),
                        domain = data.optString("domain", "")
                    )
                    AbuseIpDbData(rawJson, parsed)
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                connection?.disconnect()
            }
        }

    // 🔹 Gemini summary - FIXED VERSION
    private suspend fun getGeminiSummary(abuseIpJson: String): String =
        withContext(Dispatchers.IO) {
            val prompt = """
                Analyze this IP address data from AbuseIPDB and provide a concise security summary.
                Focus on:
                - Safety level based on abuse confidence score
                - Key risk factors
                - Geographic and network information
                - Recommendations if suspicious/malicious
                
                Keep it brief and actionable (2-3 sentences max).
                
                JSON Data: $abuseIpJson
            """.trimIndent()

            var connection: HttpURLConnection? = null
            try {
                // Fixed Gemini API endpoint and model
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                // Build the request payload
                val requestBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                    // Add safety settings to avoid blocking
                    put("safetySettings", JSONArray().apply {
                        put(JSONObject().apply {
                            put("category", "HARM_CATEGORY_HARASSMENT")
                            put("threshold", "BLOCK_NONE")
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.7)
                        put("topK", 40)
                        put("topP", 0.95)
                        put("maxOutputTokens", 512)
                    })
                }

                // Send request
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestBody.toString())
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                    val jsonResponse = JSONObject(response)

                    // Extract the generated text from response
                    val candidates = jsonResponse.getJSONArray("candidates")
                    if (candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val content = candidate.getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        if (parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).getString("text")
                        }
                    }
                    "No summary generated."
                } else {
                    // Read error stream for more details
                    val errorStream = connection.errorStream
                    val errorResponse = errorStream?.bufferedReader()?.use(BufferedReader::readText)
                    "API Error $responseCode: ${errorResponse ?: "Unknown error"}"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                "Error generating summary: ${e.message ?: "Unknown error"}"
            } finally {
                connection?.disconnect()
            }
        }
}