package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubOwner(
    val login: String = "",
    @Json(name = "avatar_url") val avatarUrl: String = ""
)

@JsonClass(generateAdapter = true)
data class GitHubRepo(
    val id: Long = 0L,
    val name: String = "",
    @Json(name = "full_name") val fullName: String = "",
    val description: String? = null,
    val language: String? = null,
    @Json(name = "stargazers_count") val stargazersCount: Long = 0L,
    @Json(name = "forks_count") val forksCount: Long = 0L,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "private") val isPrivate: Boolean = false,
    val owner: GitHubOwner = GitHubOwner()
)

@JsonClass(generateAdapter = true)
data class GitHubSearchResponse(
    @Json(name = "total_count") val totalCount: Int = 0,
    val items: List<GitHubRepo> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GitHubUser(
    val login: String = "",
    val name: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String = "",
    @Json(name = "public_repos") val publicRepos: Int = 0
)

@JsonClass(generateAdapter = true)
data class GitHubContentItem(
    val name: String = "",
    val path: String = "",
    val type: String = "file", // "file" or "dir"
    val size: Long = 0L,
    val content: String? = null,
    val encoding: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubCommitDetail(
    val message: String = "",
    val author: GitHubCommitAuthor? = null
)

@JsonClass(generateAdapter = true)
data class GitHubCommitAuthor(
    val name: String = "",
    val date: String = ""
)

@JsonClass(generateAdapter = true)
data class GitHubCommitItem(
    val sha: String = "",
    val commit: GitHubCommitDetail = GitHubCommitDetail()
)
