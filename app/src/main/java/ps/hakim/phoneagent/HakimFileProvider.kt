package ps.hakim.phoneagent

import androidx.core.content.FileProvider

/**
 * Dedicated, non-exported provider for verified Hakim intake mirrors.
 *
 * Using a concrete subclass avoids OEM/runtime edge cases from declaring the
 * AndroidX FileProvider class directly while retaining scoped content:// URIs.
 */
class HakimFileProvider : FileProvider()
