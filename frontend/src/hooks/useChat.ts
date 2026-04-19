import { useCallback, useEffect, useRef, useState } from 'react';
import { getWsUrl } from '../api';
import type { ChatMessage } from '../types';

type Status = 'disconnected' | 'connecting' | 'connected' | 'error';

export function useChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [status, setStatus] = useState<Status>('disconnected');
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;
    setStatus('connecting');
    const ws = new WebSocket(getWsUrl());

    ws.onopen = () => setStatus('connected');

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
            return [
              ...prev.slice(0, -1),
              { ...last, content: last.content + data.chunk },
            ];
          }
          return prev;
        });
      } else if (data.done) {
        setMessages((prev) => {
          const last = prev[prev.length - 1];
          if (last?.role === 'assistant') {
            return [
              ...prev.slice(0, -1),
              { ...last, streaming: false, sources: data.sources ?? [] },
            ];
          }
          return prev;
        });
      } else if (data.error) {
        setMessages((prev) => {
          const last = prev[prev.length - 1];
          if (last?.role === 'assistant' && last.streaming) {
            return [
              ...prev.slice(0, -1),
              { ...last, streaming: false, content: `Error: ${data.error}` },
            ];
          }
          return prev;
        });
      }
    };

    ws.onerror = () => setStatus('error');

    ws.onclose = () => {
      setStatus('disconnected');
      reconnectTimer.current = setTimeout(connect, 3000);
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

      const userMsg: ChatMessage = {
        id: crypto.randomUUID(),
        role: 'user',
        content: text,
      };
      const assistantMsg: ChatMessage = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: '',
        streaming: true,
      };
      setMessages((prev) => [...prev, userMsg, assistantMsg]);

      const send = () => {
        wsRef.current?.send(JSON.stringify({ message: text }));
      };

      if (wsRef.current?.readyState === WebSocket.OPEN) {
        send();
      } else {
        connect();
        const wait = setInterval(() => {
          if (wsRef.current?.readyState === WebSocket.OPEN) {
            clearInterval(wait);
            send();
          }
        }, 100);
        setTimeout(() => clearInterval(wait), 5000);
      }
    },
    [connect],
  );

  const clearMessages = useCallback(() => setMessages([]), []);

  return { messages, status, sendMessage, clearMessages };
}
