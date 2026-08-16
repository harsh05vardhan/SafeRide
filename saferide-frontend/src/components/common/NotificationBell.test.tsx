import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { NotificationBell } from "./NotificationBell";
import { notificationService } from "@/services/notificationService";
import type { Notification } from "@/types/notification";

vi.mock("@/services/notificationService", () => ({
  notificationService: {
    unreadCount: vi.fn(),
    listMine: vi.fn(),
    markAsRead: vi.fn(),
    markAllAsRead: vi.fn(),
  },
}));

const mockedService = vi.mocked(notificationService);

function sampleNotification(overrides: Partial<Notification> = {}): Notification {
  return {
    id: 1,
    type: "DRIVER_ASSIGNED",
    title: "Driver assigned",
    message: "A driver has been assigned to your ride.",
    rideId: 42,
    read: false,
    createdAt: "2026-08-16T10:00:00Z",
    ...overrides,
  };
}

function renderBell() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <NotificationBell />
    </QueryClientProvider>,
  );
}

describe("NotificationBell", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedService.unreadCount.mockResolvedValue(0);
    mockedService.listMine.mockResolvedValue({ content: [], page: { size: 10, number: 0, totalElements: 0, totalPages: 0 } });
  });

  it("shows no badge when there are no unread notifications", async () => {
    renderBell();
    await waitFor(() => expect(mockedService.unreadCount).toHaveBeenCalled());
    expect(screen.queryByText(/^\d+\+?$/)).not.toBeInTheDocument();
  });

  it("shows the unread count as a badge", async () => {
    mockedService.unreadCount.mockResolvedValue(3);
    renderBell();
    expect(await screen.findByText("3")).toBeInTheDocument();
  });

  it("caps the badge at 9+ rather than showing an arbitrarily large number", async () => {
    mockedService.unreadCount.mockResolvedValue(42);
    renderBell();
    expect(await screen.findByText("9+")).toBeInTheDocument();
  });

  it("does not fetch the notification list until the bell is opened", async () => {
    renderBell();
    await waitFor(() => expect(mockedService.unreadCount).toHaveBeenCalled());
    expect(mockedService.listMine).not.toHaveBeenCalled();

    const user = userEvent.setup();
    await user.click(screen.getByLabelText("Notifications"));

    await waitFor(() => expect(mockedService.listMine).toHaveBeenCalledWith(false, 0, 10));
  });

  it("renders the fetched notifications once opened", async () => {
    mockedService.listMine.mockResolvedValue({
      content: [sampleNotification({ id: 1, title: "Driver assigned" }), sampleNotification({ id: 2, title: "Ride completed", type: "RIDE_COMPLETED" })],
      page: { size: 10, number: 0, totalElements: 2, totalPages: 1 },
    });
    renderBell();

    const user = userEvent.setup();
    await user.click(screen.getByLabelText("Notifications"));

    expect(await screen.findByText("Driver assigned")).toBeInTheDocument();
    expect(screen.getByText("Ride completed")).toBeInTheDocument();
  });

  it("shows an empty state when there are no notifications", async () => {
    renderBell();
    const user = userEvent.setup();
    await user.click(screen.getByLabelText("Notifications"));

    expect(await screen.findByText("You're all caught up.")).toBeInTheDocument();
  });

  it("marks a notification as read when clicked, and refreshes the unread count", async () => {
    mockedService.listMine.mockResolvedValue({
      content: [sampleNotification({ id: 7 })],
      page: { size: 10, number: 0, totalElements: 1, totalPages: 1 },
    });
    mockedService.unreadCount.mockResolvedValueOnce(1).mockResolvedValue(0);
    mockedService.markAsRead.mockResolvedValue(sampleNotification({ id: 7, read: true }));

    renderBell();
    const user = userEvent.setup();
    await user.click(screen.getByLabelText("Notifications"));
    await screen.findByText("Driver assigned");

    await user.click(screen.getByText("Driver assigned"));

    await waitFor(() => expect(mockedService.markAsRead).toHaveBeenCalledWith(7));
  });

  it("marks everything read via the header action", async () => {
    mockedService.unreadCount.mockResolvedValue(2);
    mockedService.markAllAsRead.mockResolvedValue(undefined);

    renderBell();
    const user = userEvent.setup();
    await user.click(screen.getByLabelText("Notifications"));

    await user.click(await screen.findByText("Mark all read"));

    await waitFor(() => expect(mockedService.markAllAsRead).toHaveBeenCalledTimes(1));
  });

  it("closes the dropdown when clicking outside it", async () => {
    render(
      <div>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <NotificationBell />
        </QueryClientProvider>
        <button>outside</button>
      </div>,
    );
    const user = userEvent.setup();
    await user.click(screen.getByLabelText("Notifications"));
    expect(await screen.findByText("You're all caught up.")).toBeInTheDocument();

    await user.click(screen.getByText("outside"));
    await waitFor(() => expect(screen.queryByText("You're all caught up.")).not.toBeInTheDocument());
  });
});
