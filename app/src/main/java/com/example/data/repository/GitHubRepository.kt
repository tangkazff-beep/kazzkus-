package com.example.data.repository

import android.util.Base64
import com.example.data.model.GitHubCommitItem
import com.example.data.model.GitHubContentItem
import com.example.data.model.GitHubRepo
import com.example.data.model.GitHubUser
import com.example.data.network.GitHubApiService
import kotlinx.coroutines.CancellationException

class GitHubRepository(
    private val api: GitHubApiService = GitHubApiService.create()
) {
    private fun authHeader(token: String?): String? {
        val clean = token?.trim().orEmpty()
        return if (clean.isNotEmpty()) "Bearer $clean" else null
    }

    suspend fun getAuthenticatedProfile(token: String): Result<GitHubUser> = try {
        val header = authHeader(token) ?: throw IllegalArgumentException("Token GitHub kosong.")
        Result.success(api.getAuthenticatedUser(header))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getMyRepositories(token: String): Result<List<GitHubRepo>> = try {
        val header = authHeader(token) ?: throw IllegalArgumentException("Token GitHub kosong.")
        Result.success(api.getAuthenticatedUserRepos(header))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun searchRepositories(query: String, token: String?): Result<List<GitHubRepo>> = try {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            Result.success(emptyList())
        } else {
            val response = api.searchRepositories(authHeader(token), trimmed)
            Result.success(response.items)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getDirectoryContents(
        owner: String,
        repo: String,
        path: String,
        token: String?
    ): Result<List<GitHubContentItem>> = try {
        val items = api.getDirectoryContents(authHeader(token), owner, repo, path)
            .sortedWith(compareBy<GitHubContentItem> { it.type != "dir" }.thenBy { it.name.lowercase() })
        Result.success(items)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getDecodedFileContent(
        owner: String,
        repo: String,
        path: String,
        token: String?
    ): Result<String> = try {
        val item = api.getFileContent(authHeader(token), owner, repo, path)
        val raw = item.content.orEmpty().replace("\n", "")
        if (raw.isBlank()) {
            Result.success("// File kosong atau terlalu besar untuk pratinjau langsung.")
        } else if (item.encoding.equals("base64", ignoreCase = true)) {
            val decoded = String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8)
            Result.success(decoded)
        } else {
            Result.success(raw)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getRecentCommits(
        owner: String,
        repo: String,
        token: String?
    ): Result<List<GitHubCommitItem>> = try {
        Result.success(api.getRecentCommits(authHeader(token), owner, repo))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
