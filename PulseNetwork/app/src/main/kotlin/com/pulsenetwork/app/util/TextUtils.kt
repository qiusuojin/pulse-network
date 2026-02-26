package com.pulsenetwork.app.util

import java.text.Normalizer
import java.util.regex.Pattern

/**
 * 文本处理工具
 */
object TextUtils {

    // Markdown 代码块正则
    private val CODE_BLOCK_PATTERN = Pattern.compile("```(\\w*)\\n([\\s\\S]*?)```", Pattern.MULTILINE)
    private val INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`")
    private val BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+)\\*\\*")
    private val ITALIC_PATTERN = Pattern.compile("\\*([^*]+)\\*")

    /**
     * 检测文本是否为空或仅包含空白字符
     */
    fun isBlank(text: String?): Boolean {
        return text.isNullOrBlank()
    }

    /**
     * 截断文本
     */
    fun truncate(text: String, maxLength: Int, suffix: String = "..."): String {
        return if (text.length <= maxLength) {
            text
        } else {
            text.take(maxLength - suffix.length) + suffix
        }
    }

    /**
     * 计算文本的 token 估算值
     * 简单估算：英文约 4 字符 = 1 token，中文约 1.5 字符 = 1 token
     */
    fun estimateTokens(text: String): Int {
        val chineseChars = text.count { it.code > 0x4E00 && it.code < 0x9FFF }
        val otherChars = text.length - chineseChars

        return (chineseChars / 1.5 + otherChars / 4.0).toInt().coerceAtLeast(1)
    }

    /**
     * 提取代码块
     */
    fun extractCodeBlocks(text: String): List<CodeBlock> {
        val blocks = mutableListOf<CodeBlock>()
        val matcher = CODE_BLOCK_PATTERN.matcher(text)

        while (matcher.find()) {
            blocks.add(CodeBlock(
                language = matcher.group(1) ?: "",
                code = matcher.group(2) ?: ""
            ))
        }

        return blocks
    }

    /**
     * 移除 Markdown 格式
     */
    fun stripMarkdown(text: String): String {
        var result = text

        // 移除代码块
        result = CODE_BLOCK_PATTERN.matcher(result).replaceAll("$2")

        // 移除行内代码
        result = INLINE_CODE_PATTERN.matcher(result).replaceAll("$1")

        // 移除粗体
        result = BOLD_PATTERN.matcher(result).replaceAll("$1")

        // 移除斜体
        result = ITALIC_PATTERN.matcher(result).replaceAll("$1")

        return result
    }

    /**
     * 规范化文本（用于比较）
     */
    fun normalize(text: String): String {
        return Normalizer.normalize(text.lowercase().trim(), Normalizer.Form.NFKC)
    }

    /**
     * 计算两个文本的相似度 (Jaccard)
     */
    fun similarity(text1: String, text2: String): Double {
        val words1 = normalize(text1).split(Regex("\\s+")).toSet()
        val words2 = normalize(text2).split(Regex("\\s+")).toSet()

        val intersection = words1.intersect(words2)
        val union = words1.union(words2)

        return if (union.isEmpty()) 1.0 else intersection.size.toDouble() / union.size
    }
}

/**
 * 代码块
 */
data class CodeBlock(
    val language: String,
    val code: String
)
