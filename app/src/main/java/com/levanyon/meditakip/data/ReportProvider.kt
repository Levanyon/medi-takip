package com.levanyon.meditakip.data

/** Only the report cache directory is exposed, never the database or private safety backup. */
class ReportProvider : androidx.core.content.FileProvider()
