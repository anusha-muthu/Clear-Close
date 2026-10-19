
const API_URL = "http://localhost:8080";

async function getErrorMessage(response, fallbackMessage) {
  try {
    const body = await response.json();
    return body.message || body.error || fallbackMessage;
  } catch {
    return fallbackMessage;
  }
}

// Borrower Login
export async function loginBorrower(username, password) {
  const response = await fetch(
    `${API_URL}/api/auth/borrower/login`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        username,
        password,
      }),
    }
  );

  if (!response.ok) {
    throw new Error(
      await getErrorMessage(response, "Invalid username or password"),
    );
  }

  return response.json();
}

// Fetch all loans of logged-in borrower
export async function getMyLoans(token) {
  const response = await fetch(
    `${API_URL}/api/borrower/loans/list`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  );

  if (!response.ok) {
    const fallback = response.status === 401
      ? "Session expired. Please log in again."
      : response.status === 403
        ? "You are not allowed to access these loans."
        : "Unable to fetch loans. Please try again.";

    throw new Error(await getErrorMessage(response, fallback));
  }

  return response.json();
}

// Fetch one specific loan
export async function getLoanById(token, loanId) {
  const response = await fetch(
    `${API_URL}/api/borrower/loans/${loanId}`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  );

  if (!response.ok) {
    throw new Error(
      await getErrorMessage(response, "Unable to fetch loan details."),
    );
  }

  return response.json();
}
