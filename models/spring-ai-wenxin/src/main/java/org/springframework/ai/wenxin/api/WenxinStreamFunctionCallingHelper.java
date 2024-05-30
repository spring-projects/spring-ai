package org.springframework.ai.wenxin.api;

import org.springframework.ai.wenxin.api.WenxinApi.ChatCompletionChunk;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lvchzh
 * @date 2024年05月14日 下午4:52
 * @description:
 */
public class WenxinStreamFunctionCallingHelper {

	public ChatCompletionChunk merge(ChatCompletionChunk previous, ChatCompletionChunk current) {
		if (previous == null) {
			return current;
		}

		String id = (current.id() != null ? current.id() : previous.id());
		String object = (current.object() != null ? current.object() : previous.object());
		Long created = (current.created() != null ? current.created() : previous.created());
		String sentenceId = (current.sentenceId() != null ? current.sentenceId() : previous.sentenceId());
		Boolean isEnd = (current.isEnd() != null ? current.isEnd() : previous.isEnd());
		Boolean isTruncated = (current.isTruncated() != null ? current.isTruncated() : previous.isTruncated());
		Boolean needClearHistory = (current.needClearHistory() != null ? current.needClearHistory()
				: previous.needClearHistory());
		String result = (current.result() != null ? current.result() : previous.result());
		Integer flag = (current.flag() != null ? current.flag() : previous.flag());
		Integer banRound = (current.banRound() != null ? current.banRound() : previous.banRound());

		WenxinApi.ChatCompletionFinishReason finishReason = (current.finishReason() != null ? current.finishReason()
				: previous.finishReason());

		WenxinApi.ChatCompletion.SearchInfo searchInfo = merge(previous.searchInfo(), current.searchInfo());

		WenxinApi.FunctionCall functionCall = merge(previous.functionCall(), current.functionCall());

		WenxinApi.Usage usage = merge(previous.usage(), current.usage());

		return new ChatCompletionChunk(id, object, created, sentenceId, isEnd, isTruncated, finishReason, searchInfo,
				result, needClearHistory, flag, banRound, usage, functionCall);

	}

	private WenxinApi.ChatCompletion.SearchInfo merge(WenxinApi.ChatCompletion.SearchInfo previous,
			WenxinApi.ChatCompletion.SearchInfo current) {
		if (previous == null) {
			return current;
		}

		List<WenxinApi.SearchResult> searchResults = new ArrayList<>();
		WenxinApi.SearchResult lastPreviousSearchResult = null;
		if (previous.searchResults() != null) {
			lastPreviousSearchResult = previous.searchResults().get(previous.searchResults().size() - 1);
			if (previous.searchResults() != null) {
				searchResults.addAll(previous.searchResults().subList(0, previous.searchResults().size() - 1));
			}
		}
		if (current.searchResults() != null) {
			if (current.searchResults().size() > 1) {
				throw new IllegalArgumentException("Currently only one tool call is supported per message!");
			}
			var currentSearchResult = current.searchResults().iterator().next();
			if (currentSearchResult.index() != null) {
				if (lastPreviousSearchResult != null) {
					searchResults.add(lastPreviousSearchResult);
				}
				searchResults.add(currentSearchResult);
			}
			else {
				searchResults.add(merge(lastPreviousSearchResult, currentSearchResult));
			}
		}
		else {
			if (lastPreviousSearchResult != null) {
				searchResults.add(lastPreviousSearchResult);
			}
		}
		return new WenxinApi.ChatCompletion.SearchInfo(searchResults);
	}

	private WenxinApi.SearchResult merge(WenxinApi.SearchResult previous, WenxinApi.SearchResult current) {
		if (previous != null) {
			return current;
		}

		Integer id = current.index() != null ? current.index() : previous.index();
		String title = current.title() != null ? current.title() : previous.title();
		String url = current.url() != null ? current.url() : previous.url();

		return new WenxinApi.SearchResult(id, title, url);
	}

	private WenxinApi.FunctionCall merge(WenxinApi.FunctionCall previous, WenxinApi.FunctionCall current) {
		if (previous == null) {
			return current;
		}

		String name = current.name() != null ? current.name() : previous.name();
		String thoughts = current.thoughts() != null ? current.thoughts() : previous.thoughts();
		StringBuilder arguments = new StringBuilder();
		if (previous.arguments() != null) {
			arguments.append(previous.arguments());
		}
		if (current.arguments() != null) {
			arguments.append(current.arguments());
		}

		return new WenxinApi.FunctionCall(name, arguments.toString(), thoughts);

	}

	private WenxinApi.Usage merge(WenxinApi.Usage previous, WenxinApi.Usage current) {
		if (previous == null) {
			return current;
		}

		Integer promptTokens = current.promptTokens() != null ? current.promptTokens() : previous.promptTokens();
		Integer completionTokens = current.completionTokens() != null ? current.completionTokens()
				: previous.completionTokens();
		Integer totalTokens = current.totalTokens() != null ? current.totalTokens() : previous.totalTokens();

		List<WenxinApi.Usage.PluginUsage> plugins = new ArrayList<>();
		WenxinApi.Usage.PluginUsage lastPreviousPluginUsage = null;
		if (previous.plugins() != null) {
			lastPreviousPluginUsage = previous.plugins().get(previous.plugins().size() - 1);
			if (previous.plugins().size() > 1) {
				plugins.addAll(previous.plugins().subList(0, previous.plugins().size() - 1));
			}
		}
		if (current.plugins() != null) {
			if (current.plugins().size() > 1) {
				throw new IllegalArgumentException("Currently only one tool call is supported per message!");
			}
			var currentPluginUsage = current.plugins().iterator().next();
			if (currentPluginUsage.name() != null) {
				if (lastPreviousPluginUsage != null) {
					plugins.add(lastPreviousPluginUsage);
				}
				plugins.add(currentPluginUsage);
			}
			else {
				plugins.add(merge(lastPreviousPluginUsage, currentPluginUsage));
			}
		}
		else {
			if (lastPreviousPluginUsage != null) {
				plugins.add(lastPreviousPluginUsage);
			}
		}
		return new WenxinApi.Usage(promptTokens, completionTokens, totalTokens, plugins);
	}

	private WenxinApi.Usage.PluginUsage merge(WenxinApi.Usage.PluginUsage previous,
			WenxinApi.Usage.PluginUsage current) {
		if (previous == null) {
			return current;
		}

		String name = current.name() != null ? current.name() : previous.name();
		Integer parseTokens = current.parseTokens() != null ? current.parseTokens() : previous.parseTokens();
		Integer abstractTokens = current.abstractTokens() != null ? current.abstractTokens()
				: previous.abstractTokens();
		Integer searchTokens = current.searchTokens() != null ? current.searchTokens() : previous.searchTokens();
		Integer totalTokens = current.totalTokens() != null ? current.totalTokens() : previous.totalTokens();

		return new WenxinApi.Usage.PluginUsage(name, parseTokens, abstractTokens, searchTokens, totalTokens);
	}

	public boolean isStreamingToolFunctionCall(ChatCompletionChunk chatCompletion) {

		if (chatCompletion == null || chatCompletion.functionCall() == null) {
			return false;
		}

		return true;
	}

	public boolean isStreamingToolFunctionCallFinish(ChatCompletionChunk chatCompletion) {

		if (chatCompletion == null || chatCompletion.functionCall() == null) {
			return false;
		}

		return chatCompletion.finishReason() == WenxinApi.ChatCompletionFinishReason.FUNCTION_CALL;

	}

}
