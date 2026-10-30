package com.example.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import com.example.data.repository.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ToolManager(
    private val context: Context,
    private val memoryRepository: MemoryRepository
) {
    private val _toolNotifications = MutableSharedFlow<ToolNotificationState>(extraBufferCapacity = 10)
    val toolNotifications: SharedFlow<ToolNotificationState> = _toolNotifications.asSharedFlow()

    /**
     * Builds the Gemini Live function declarations JSON specification.
     */
    fun getToolsDeclarationJson(): JSONArray {
        val toolsArray = JSONArray()
        val funcList = JSONArray()

        // 1. openWebsite
        funcList.put(JSONObject().apply {
            put("name", "openWebsite")
            put("description", "Opens an external HTTPS website or web app in the browser for the user.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("url", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The validated HTTPS URL to open (e.g., https://youtube.com, https://google.com)")
                    })
                    put("title", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "User-friendly name of the platform (e.g., YouTube, Twitter, GitHub)")
                    })
                })
                put("required", JSONArray().apply { put("url") })
            })
        })

        // 2. saveMemory
        funcList.put(JSONObject().apply {
            put("name", "saveMemory")
            put("description", "Saves a persistent user preference, personal fact, goal, or important information to long-term memory.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("fact", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The specific fact or preference to remember.")
                    })
                    put("category", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Category: preference, goal, project, personal, or summary.")
                    })
                    put("isConfirmed", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "True if the user directly stated it, false if it is a deduction.")
                    })
                })
                put("required", JSONArray().apply { put("fact") })
            })
        })

        // 3. searchMemories
        funcList.put(JSONObject().apply {
            put("name", "searchMemories")
            put("description", "Searches through long-term saved memories to answer questions about user preferences, facts, or past discussions.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Keywords or concept to search for in memories.")
                    })
                })
                put("required", JSONArray().apply { put("query") })
            })
        })

        // 4. createNote
        funcList.put(JSONObject().apply {
            put("name", "createNote")
            put("description", "Creates a structured text note or checklist for the user.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("title", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Title of the note.")
                    })
                    put("content", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The full content of the note.")
                    })
                })
                put("required", JSONArray().apply {
                    put("title")
                    put("content")
                })
            })
        })

        // 5. searchNotes
        funcList.put(JSONObject().apply {
            put("name", "searchNotes")
            put("description", "Searches the user's saved notes.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Keywords to search notes by.")
                    })
                })
                put("required", JSONArray().apply { put("query") })
            })
        })

        // 6. setTimer
        funcList.put(JSONObject().apply {
            put("name", "setTimer")
            put("description", "Sets a timer for a given duration in minutes.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("minutes", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Duration of the timer in minutes.")
                    })
                    put("label", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Reason or label for the timer.")
                    })
                })
                put("required", JSONArray().apply { put("minutes") })
            })
        })

        // 7. getDeviceContext
        funcList.put(JSONObject().apply {
            put("name", "getDeviceContext")
            put("description", "Gets current device status including current date and time, battery percentage, and memory stats.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("includeStats", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "Optional flag to include memory statistics.")
                    })
                })
            })
        })

        toolsArray.put(JSONObject().apply {
            put("functionDeclarations", funcList)
        })

        return toolsArray
    }

    /**
     * Executes a function call from Gemini and returns tool output.
     */
    suspend fun executeTool(callId: String, name: String, args: JSONObject): ToolExecutionResult = withContext(Dispatchers.IO) {
        try {
            when (name) {
                "openWebsite" -> {
                    val rawUrl = args.optString("url", "").trim()
                    val title = args.optString("title", "Website")
                    val validation = validateAndSanitizeUrl(rawUrl)
                    if (!validation.first) {
                        return@withContext ToolExecutionResult(
                            callId = callId,
                            toolName = name,
                            success = false,
                            message = "Invalid URL rejected for security",
                            resultJson = JSONObject().apply {
                                put("error", "URL rejected: only secure HTTPS URLs are permitted.")
                            }.toString()
                        )
                    }

                    val sanitizedUrl = validation.second
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sanitizedUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)

                    val userMessage = "Opened $title ($sanitizedUrl) for you! 😉"
                    _toolNotifications.tryEmit(ToolNotificationState(callId, name, userMessage))

                    ToolExecutionResult(
                        callId = callId,
                        toolName = name,
                        success = true,
                        message = userMessage,
                        resultJson = JSONObject().apply {
                            put("openedUrl", sanitizedUrl)
                            put("status", "success")
                        }.toString()
                    )
                }

                "saveMemory" -> {
                    val fact = args.optString("fact", "").trim()
                    val category = args.optString("category", "preference").trim()
                    val isConfirmed = args.optBoolean("isConfirmed", true)

                    if (fact.isEmpty()) {
                        return@withContext ToolExecutionResult(
                            callId = callId,
                            toolName = name,
                            success = false,
                            message = "Empty fact cannot be saved",
                            resultJson = JSONObject().apply { put("error", "Empty fact") }.toString()
                        )
                    }

                    val memId = memoryRepository.saveOrUpdateMemory(
                        content = fact,
                        category = category,
                        sourceContext = "Live voice session",
                        isConfirmed = isConfirmed
                    )

                    val userMsg = "Locked into memory: \"$fact\" 🧠✨"
                    _toolNotifications.tryEmit(ToolNotificationState(callId, name, userMsg))

                    ToolExecutionResult(
                        callId = callId,
                        toolName = name,
                        success = true,
                        message = userMsg,
                        resultJson = JSONObject().apply {
                            put("memoryId", memId)
                            put("fact", fact)
                            put("category", category)
                            put("status", "saved")
                        }.toString()
                    )
                }

                "searchMemories" -> {
                    val query = args.optString("query", "").trim()
                    val results = memoryRepository.searchMemories(query)
                    val jsonArray = JSONArray()
                    for (m in results) {
                        jsonArray.put(JSONObject().apply {
                            put("category", m.category)
                            put("fact", m.content)
                            put("isConfirmed", m.isConfirmed)
                        })
                    }

                    val userMsg = "Searched memory vault for \"$query\" (${results.size} matches)"
                    _toolNotifications.tryEmit(ToolNotificationState(callId, name, userMsg))

                    ToolExecutionResult(
                        callId = callId,
                        toolName = name,
                        success = true,
                        message = userMsg,
                        resultJson = JSONObject().apply {
                            put("matchesCount", results.size)
                            put("results", jsonArray)
                        }.toString()
                    )
                }

                "createNote" -> {
                    val title = args.optString("title", "Voice Note").trim()
                    val content = args.optString("content", "").trim()
                    val noteId = memoryRepository.createNote(title, content)

                    val userMsg = "Saved note: \"$title\" 📝"
                    _toolNotifications.tryEmit(ToolNotificationState(callId, name, userMsg))

                    ToolExecutionResult(
                        callId = callId,
                        toolName = name,
                        success = true,
                        message = userMsg,
                        resultJson = JSONObject().apply {
                            put("noteId", noteId)
                            put("title", title)
                            put("status", "created")
                        }.toString()
                    )
                }

                "searchNotes" -> {
                    val query = args.optString("query", "").trim()
                    val notes = memoryRepository.searchNotes(query)
                    val notesArray = JSONArray()
                    for (n in notes) {
                        notesArray.put(JSONObject().apply {
                            put("id", n.id)
                            put("title", n.title)
                            put("content", n.content)
                        })
                    }

                    ToolExecutionResult(
                        callId = callId,
                        toolName = name,
                        success = true,
                        message = "Found ${notes.size} notes",
                        resultJson = JSONObject().apply {
                            put("count", notes.size)
                            put("notes", notesArray)
                        }.toString()
                    )
                }

                "setTimer" -> {
                    val minutes = args.optInt("minutes", 0)
                    val label = args.optString("label", "Roxy Timer")
                    if (minutes <= 0) {
                        return@withContext ToolExecutionResult(
                            callId = callId,
                            toolName = name,
                            success = false,
                            message = "Timer must be greater than 0 minutes",
                            resultJson = JSONObject().apply { put("error", "Invalid minutes") }.toString()
                        )
                    }

                    val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                        putExtra(AlarmClock.EXTRA_MESSAGE, label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }

                    val launched = runCatching {
                        context.startActivity(timerIntent)
                        true
                    }.getOrDefault(false)

                    val userMsg = "Timer set for $minutes min: \"$label\" ⏱️"
                    _toolNotifications.tryEmit(ToolNotificationState(callId, name, userMsg))

                    ToolExecutionResult(
                        callId = callId,
                        toolName = name,
                        success = launched,
                        message = userMsg,
                        resultJson = JSONObject().apply {
                            put("minutes", minutes)
                            put("label", label)
                            put("success", launched)
                        }.toString()
                    )
                }

                "getDeviceContext" -> {
                    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                    val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy HH:mm:ss", Locale.getDefault())
                    val currentTime = sdf.format(Date())

                    ToolExecutionResult(
                        callId = callId,
                        toolName = name,
                        success = true,
                        message = "Device context retrieved",
                        resultJson = JSONObject().apply {
                            put("currentTime", currentTime)
                            put("batteryPercentage", batteryLevel)
                            put("masterCreator", "Ayush")
                        }.toString()
                    )
                }

                else -> {
                    ToolExecutionResult(
                        callId = callId,
                        toolName = name,
                        success = false,
                        message = "Unknown tool: $name",
                        resultJson = JSONObject().apply { put("error", "Unknown function") }.toString()
                    )
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult(
                callId = callId,
                toolName = name,
                success = false,
                message = "Tool execution failed: ${e.message}",
                resultJson = JSONObject().apply { put("error", e.message) }.toString()
            )
        }
    }

    /**
     * Strict URL validation ensuring only HTTPS web destinations are launched.
     */
    private fun validateAndSanitizeUrl(input: String): Pair<Boolean, String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Pair(false, "")

        // Check if a dangerous or non-web scheme is present
        val colonIndex = trimmed.indexOf(':')
        val slashIndex = trimmed.indexOf('/')
        if (colonIndex != -1 && (slashIndex == -1 || colonIndex < slashIndex)) {
            val scheme = trimmed.substring(0, colonIndex).lowercase(Locale.ROOT)
            if (scheme != "https" && scheme != "http") {
                return Pair(false, "")
            }
        }

        val formatted = if (trimmed.startsWith("http://", ignoreCase = true)) {
            "https://" + trimmed.substring(7)
        } else if (!trimmed.startsWith("https://", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }

        val uri = runCatching { Uri.parse(formatted) }.getOrNull() ?: return Pair(false, "")
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host

        if (scheme != "https" || host.isNullOrBlank() || !host.contains('.')) {
            return Pair(false, "")
        }

        return Pair(true, formatted)
    }
}
