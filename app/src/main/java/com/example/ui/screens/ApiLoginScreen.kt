package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.remote.DjangoApiClient
import java.net.UnknownHostException
import org.json.JSONObject
import kotlinx.coroutines.launch

@Composable
fun ApiLoginScreen(
    apiClient: DjangoApiClient,
    onSignedIn: (JSONObject) -> Unit
) {
    var apiUrl by remember { mutableStateOf(apiClient.baseUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }
    var verifiedSchoolName by remember {
        mutableStateOf(apiClient.configuredSchoolName.takeIf(String::isNotBlank))
    }
    var isVerifyingSchool by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Eden Boarding", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (apiClient.tenantConfigured) {
                    "Sign in with your school staff account for ${apiClient.configuredSchoolName}. Access is controlled by the Django server."
                } else {
                    "Initial school setup must be completed by an administrator. The selected school will be locked to this device after setup."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your role is assigned to your staff account by the school administrator. " +
                    "It cannot be selected or changed in the app; sign in with an authorized account for that role.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            if (apiClient.tenantConfigured) {
                Text(
                    text = "School: ${apiClient.configuredSchoolName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                OutlinedTextField(
                    value = apiUrl,
                    onValueChange = {
                        apiUrl = it
                        verifiedSchoolName = null
                        errorMessage = null
                    },
                    label = { Text("School server URL") },
                    placeholder = { Text("https://atebubushs.ecreativesys.com/api/v1") },
                    supportingText = {
                        Text(
                            "Choose the school server during first-time setup. Production uses HTTPS; " +
                                "the app adds /api/v1 if omitted."
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        errorMessage = null
                        verifiedSchoolName = null
                        isVerifyingSchool = true
                        scope.launch {
                            try {
                                val preview = apiClient.previewSchool(apiUrl)
                                val schoolName = preview.optJSONObject("school")
                                    ?.optString("name")
                                    ?.takeIf(String::isNotBlank)
                                verifiedSchoolName = schoolName
                                if (schoolName == null) {
                                    errorMessage = "The server did not identify a school."
                                } else {
                                    apiClient.rememberSchoolSelection(apiUrl, schoolName)
                                }
                            } catch (error: Exception) {
                                verifiedSchoolName = null
                                errorMessage = loginErrorMessage(
                                    error,
                                    "Could not verify this school server."
                                )
                            } finally {
                                isVerifyingSchool = false
                            }
                        }
                    },
                    enabled = !isVerifyingSchool && apiUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isVerifyingSchool) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Verify school")
                    }
                }
                verifiedSchoolName?.let { schoolName ->
                    Text(
                        text = "Verified school: $schoolName",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    errorMessage = null
                    isSigningIn = true
                    scope.launch {
                        try {
                            val session = apiClient.login(
                                apiUrl,
                                username,
                                password,
                                expectedSchoolName = verifiedSchoolName.takeUnless {
                                    apiClient.tenantConfigured
                                }
                            )
                            password = ""
                            onSignedIn(session)
                        } catch (error: Exception) {
                            errorMessage = loginErrorMessage(
                                error,
                                "Could not sign in to the school server."
                            )
                        } finally {
                            isSigningIn = false
                        }
                    }
                },
                enabled = !isSigningIn &&
                    apiUrl.isNotBlank() &&
                    username.isNotBlank() &&
                    password.isNotBlank() &&
                    (apiClient.tenantConfigured || verifiedSchoolName != null),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Sign in")
                }
            }
        }
    }
}

private fun loginErrorMessage(error: Throwable, fallback: String): String {
    val isDnsFailure = generateSequence(error) { it.cause }
        .any { it is UnknownHostException }
    if (isDnsFailure) {
        return "Could not find the school server (DNS lookup failed). Check Wi-Fi or mobile data. " +
            "If your connection works elsewhere, set Android Private DNS to Automatic and try again."
    }
    return error.message?.takeIf(String::isNotBlank) ?: fallback
}
