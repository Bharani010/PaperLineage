import { useCallback, useEffect, useRef, useState } from 'react';
import { getWsUrl } from '../api';
import type { ChatMessage } from '../types';

type Status = 'disconnected' | 'connecting' | 'connected' | 'error';

const MAX_RETRIES = 5;
const BASE_DELAY_MS = 2000;

export function useChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [status, setStatus] = useState<Status>('disconnected');
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryCount = useRef(0);

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;
    if (retryCount.current >= MAX_RETRIES) {
      setStatus('error');
      return;
    }

    setStatus('connecting');
    const ws = new WebSocket(getWsUrl());

    ws.onopen = () => {
      retryCount.current = 0;
      setStatus('connected');
    };

    ws.onmessage = (event) => {
      const data = JSON.parse(event.data as string) as {
        chunk?: string;
        done?: boolean;
        sources?: string[];
        error?: string;
      };

      if (data.chunk !== undefined) {
        setMessages((prev) => {
          const last = prev[prev.length - 1];
          if (last?.role === 'assistant' && last.streaming) {
            return [...prev.slice(0, -1), { ...last, content: last.content + data.chunk }];
          }
          return prev;
        });
      } else if (data.done) {
        setMessages((prev) => {
          const last = prev[prev.length - 1];
          if (last?.role === 'assistant') {
            return [...prev.slice(0, -1), { ...last, streaming: false, sources: data.sources ?? [] }];
          }
          return prev;
        });
      } else if (data.error) {
        setMessages((prev) => {
          const last = prev[prev.length - 1];
          if (last?.role === 'assistant' && last.streaming) {
            return [...prev.slice(0, -1), { ...last, streaming: false, content: `Error: ${data.error}` }];
          }
          return prev;
        });
      }
    };

    ws.onerror = () => setStatus('error');

    ws.onclose = () => {
      setStatus('disconnected');
      retryCount.current += 1;
      if (retryCount.current < MAX_RETRIES) {
        const delay = BASE_DELAY_MS * Math.pow(2, retryCount.current - 1);
        reconnectTimer.current = setTimeout(connect, delay);
      } else {
        setStatus('error');
      }
    };

    wsRef.current = ws;
  }, []);

  useEffect(() => {
    connect();
    return () => {
      reconnectTimer.current && clearTimeout(reconnectTimer.current);
      wsRef.current?.close();
    };
  }, [connect]);

  const sendMessage = useCallback(
    (text: string) => {
      if (!text.trim()) return;

      const userMsg: ChatMessage = { id: crypto.randomUUID(), role: 'user', content: text };
      const assistantMsg: ChatMessage = { id: crypto.randomUUID(), role: 'assistant', content: '', streaming: true };
      setMessages((prev) => [...prev, userMsg, assistantMsg]);

      const send = () => wsRef.current?.send(JSON.stringify({ message: text }));

      if (wsRef.current?.readyState === WebSocket.OPEN) {
        send();
      } else {
        retryCount.current = 0;
        connect();
        const deadline = Date.now() + 8000;
        const wait = setInterval(() => {
          if (wsRef.current?.readyState === WebSocket.OPEN) {
            clearInterval(wait);
            send();
          } else if (Date.now() > deadline) {
            clearInterval(wait);
            setMessages((prev) => {
              const last = prev[prev.length - 1];
              if (last?.streaming) {
                return [...prev.slice(0, -1), { ...last, streaming: false, content: 'Could not connect to backend. Check VITE_API_BASE_URL.' }];
              }
              return prev;
            });
          }
        }, 200);
      }
    },
    [connect],
  );

  const clearMessages = useCallback(() => setMessages([]), []);

  const reconnect = useCallback(() => {
    retryCount.current = 0;
    connect();
  }, [connect]);

  return { messages, status, sendMessage, clearMessages, reconnect };
}
